#!groovy


timestamps {
    def CANNED_CI_MESSAGE = '{"deliveryTag":584,"msg":{"attribute":"state","build_id":1430989,"epoch":null,"instance":"primary","name":"file-roller","new":1,"old":0,"owner":"kalev","release":"1.fc32","request":["git+https://src.fedoraproject.org/rpms/file-roller.git#e00aa0590662351c540d0a2782382ee45fa6bba5","f32-build-side-18035",{"skip_tag":true}],"task_id":40809920,"version":"3.35.1"},"msg_id":"6b5854b1-550e-4bf7-b145-fa6c406903e1","timestamp":1579599505254,"topic":"org.fedoraproject.prod.buildsys.build.state.change"}'

    def libraries = ['fedora-ci-generic-checks': ['master', 'https://github.com/tflink/fedora-ci-generic-checks.git'],
                     'cico-pipeline'           : ['master', 'https://github.com/CentOS/cico-pipeline-library.git'],
                     'contra-lib'              : ['master', 'https://github.com/openshift/contra-lib.git']]

    libraries.each { name, repo ->
        library identifier: "${name}@${repo[0]}",
                retriever: modernSCM([$class: 'GitSCMSource',
                                      remote: repo[1]])

    }

    // send staging messages
    env.MSG_PROVIDER = "fedora-fedmsg"

    // we don't work with scratch builds for now
    env.isScratch = false

    properties(
            [
                    buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '500', daysToKeepStr: '', numToKeepStr: '500')),
                    parameters(
                            [
                                    string(description: 'FedoraMessagingStage', defaultValue: CANNED_CI_MESSAGE, name: 'CI_MESSAGE')
                            ]
                    ),
                    pipelineTriggers(
                            [[$class: 'CIBuildTrigger',
                              noSquash: true,
                              providerData: [
                                  $class: 'RabbitMQSubscriberProviderData',
                                  name: 'FedoraMessaging',
                                  overrides: [
                                      topic: 'org.fedoraproject.prod.buildsys.build.state.change'
                                  ],
                                  checks: [
                                      [field: 'new', expectedValue: '1|CLOSED'],
                                      [field: 'release', expectedValue: '.*fc.*'],
                                      [field: 'instance', expectedValue: 'primary'],
                                      [field: 'owner', expectedValue: '^(?!koschei).*']
                                  ]
                              ]
                            ]]
                    )
            ]
    )

    def TRIGGER_RETRY_COUNT = 3
    def stepName = null

    node('master') {
        buildCheckUtils.ciPipeline {
            try {
                stepName = 'extract information'
                stage(stepName) {
                    buildCheckUtils.handlePipelineStep(stepName: stepName, debug: true) {

                    print "CI_MESSAGE"
                    print CI_MESSAGE

                    parsedMsg = buildCheckUtils.parseKojiMessage(message: env.CI_MESSAGE, ignoreErrors: false)
                    primaryKoji = parsedMsg['instance'] == "primary"
                    env.task_id = parsedMsg['task_id']
                    currentBuild.displayName = "BUILD#: ${env.BUILD_NUMBER}"

                    // we only care about koji-builds for now
                    env.artifact = 'koji-build'

                    // set env vars needed for sending messages
                    buildCheckUtils.setDefaultEnvVars()
                    //buildCheckUtils.setScratchVars(parsedMsg)
                    }
                }

                stepName = 'schedule build'
                stage(stepName) {

                    if (primaryKoji) {
                    checks = ['rpminspect']
                        for(checkname in checks) {

                            print "Triggering for koji_taskid " + env.task_id
                            print "message: " + env.CI_MESSAGE
                            print "newmessage: " + parsedMsg.toString()

                            retry(TRIGGER_RETRY_COUNT) {
                                buildCheckUtils.handlePipelineStep(stepName: stepName, debug: true) {

                                //build job: "fedora-${checkname}",
                                build job: "fedora-${checkname}",
                                    // Scratch messages from task.state.changed call it id, not task_id
                                    parameters: [string(name: 'PROVIDED_KOJI_TASKID', value: env.task_id),
                                                string(name: 'CI_MESSAGE', value: env.CI_MESSAGE)],
                                    wait: false
                                }
                            }
                            messageFields = buildCheckUtils.setTestMessageFields("queued", artifact, parsedMsg)
                            buildCheckUtils.sendMessage(messageFields['topic'], messageFields['properties'], messageFields['content'])

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

