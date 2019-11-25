#!groovy


timestamps {
    def libraries = ['upstream-fedora-pipeline': ['master', 'https://github.com/CentOS-PaaS-SIG/upstream-fedora-pipeline.git'],
                     'contra-lib'              : ['master', 'https://github.com/openshift/contra-lib.git']]

    libraries.each { name, repo ->
        library identifier: "${name}@${repo[0]}",
                retriever: modernSCM([$class: 'GitSCMSource',
                                      remote: repo[1]])

    }

    properties(
            [
                    buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '500', daysToKeepStr: '', numToKeepStr: '500')),
                    parameters(
                            [
                                    string(description: 'fedora-fedmsg', defaultValue: '{}', name: 'CI_MESSAGE')
                            ]
                    ),
                    pipelineTriggers(
                            [[$class: 'CIBuildTrigger',
                              noSquash: true,
                              providerData: [
                                  $class: 'FedMsgSubscriberProviderData',
                                  name: 'fedora-fedmsg',
                                  overrides: [
                                      topic: 'org.fedoraproject.prod.buildsys.task.state.change'
                                  ],
                                  checks: [
                                      [field: 'new', expectedValue: '1|CLOSED'],
                                      [field: 'owner', expectedValue: '^(?!koschei).*']
                                  ]
                              ]
                            ]]
                    )
            ]
    )


    node('master') {
        packagepipelineUtils.ciPipeline {
            try {
                stepName = 'extract information'
                stage(stepName) {
                    packagepipelineUtils.handlePipelineStep(stepName: stepName, debug: true) {

                    print "CI_MESSAGE"
                    print CI_MESSAGE

                    primaryKoji = parsedMsg['instance'] == "primary"
                    currentBuild.displayName = "BUILD#: ${env.BUILD_NUMBER}"
                    }
                }

                stepName = 'schedule build'
                stage(stepName) {

                    checks = ['rpminspect']
                    for(checkname in checks) {

                        retry(TRIGGER_RETRY_COUNT) {
                            packagepipelineUtils.handlePipelineStep(stepName: stepName, debug: true) {

                            build job: "fedora-${checkname}",
                                // Scratch messages from task.state.changed call it id, not task_id
                                parameters: [string(name: 'PROVIDED_KOJI_TASKID', value: env.task_id),
                                            string(name: 'CI_MESSAGE', value: env.CI_MESSAGE)],
                                wait: false
                            }
                        }
                    }
                }
            } catch (e) {
                currentBuild.result = 'FAILURE'
                throw e
            }
        }
    }
}

