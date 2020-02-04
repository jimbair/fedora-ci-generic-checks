#!groovy

import groovy.json.JsonOutput

// this is for debug, printing stacktrace inline
import java.io.StringWriter
import java.io.PrintWriter
import org.codehaus.groovy.runtime.StackTraceUtils

timestamps {
    // CANNED CI_MESSAGE
    def CANNED_CI_MESSAGE = '{"deliveryTag":584,"msg":{"attribute":"state","build_id":1430989,"epoch":null,"instance":"primary","name":"file-roller","new":1,"old":0,"owner":"kalev","release":"1.fc32","request":["git+https://src.fedoraproject.org/rpms/file-roller.git#e00aa0590662351c540d0a2782382ee45fa6bba5","f32-build-side-18035",{"skip_tag":true}],"task_id":40809920,"version":"3.35.1"},"msg_id":"6b5854b1-550e-4bf7-b145-fa6c406903e1","timestamp":1579599505254,"topic":"org.fedoraproject.prod.buildsys.build.state.change"}'

    // Initialize all the ghprb variables we need
    env.ghprbGhRepository = env.ghprbGhRepository ?: 'tflink/fedora-ci-generic-checks'
    //env.ghprbActualCommit = env.ghprbActualCommit ?: 'master'
    env.ghprbActualCommit = env.ghprbActualCommit ?: 'feature/jobsdlattempt'
    env.ghprbPullAuthorLogin = env.ghprbPullAuthorLogin ?: ''
    env.ghprbPullId = env.ghprbPullId ?: ''

    // Task ID to bypass rpm build and grab artifacts from koji
    env.PROVIDED_KOJI_TASKID = env.PROVIDED_KOJI_TASKID ?: ''
    // Default to build being scratch, will be overridden if triggered by nonscratch build
    //env.isScratch = true
    env.PAGURE_URL = env.PAGURE_URL ?: 'https://src.fedoraproject.org'

    // Needed for podTemplate()
    env.SLAVE_TAG = env.SLAVE_TAG ?: 'stable'
    env.FEDORACI_RUNNER_TAG = env.FEDORACI_RUNNER_TAG ?: 'stable'

    // Execution ID for this run of the pipeline
    def executionID = UUID.randomUUID().toString()
    env.pipelineId = env.pipelineId ?: executionID

    // Pod name to use
    def podName = 'fedora-package-check-' + executionID

    // Number of CPU cores for the package-checks container
    runnerCpuLimit = '1'

    podTemplate(name: podName,
                label: podName,
                cloud: 'openshift',
                serviceAccount: OPENSHIFT_SERVICE_ACCOUNT,
                idleMinutes: 0,
                namespace: OPENSHIFT_NAMESPACE,

            containers: [
                    // This adds the custom slave container to the pod. Must be first with name 'jnlp'
                    containerTemplate(name: 'jnlp',
                            alwaysPullImage: true,
                            image: DOCKER_REPO_URL + '/' + OPENSHIFT_NAMESPACE + '/jenkins-fedoraci-slave:' + SLAVE_TAG,
                            ttyEnabled: false,
                            args: '${computer.jnlpmac} ${computer.name}',
                            command: '',
                            workingDir: '/workDir'),
                    // This adds the package-checks container to the pod.
                    containerTemplate(name: 'package-checks',
                            alwaysPullImage: true,
                            image: DOCKER_REPO_URL + '/' + OPENSHIFT_NAMESPACE + '/fedora-generic-check-worker:' + FEDORACI_RUNNER_TAG,
                            ttyEnabled: true,
                            command: 'cat',
                            envVars: [
                                envVar(key: 'STR_CPU_LIMIT', value: runnerCpuLimit)
                            ],
                            // Request - minimum required, Limit - maximum possible (hard quota)
                            // https://kubernetes.io/docs/concepts/configuration/manage-compute-resources-container/#meaning-of-cpu
                            // https://blog.openshift.com/managing-compute-resources-openshiftkubernetes/
                            resourceRequestCpu: '1',
                            resourceLimitCpu: runnerCpuLimit,
                            resourceRequestMemory: '4Gi',
                            resourceLimitMemory: '6Gi',
                            privileged: false,
                            //privileged: true,
                            workingDir: '/workDir')
            ],
            volumes: [emptyDirVolume(memory: false, mountPath: '/sys/class/net')])
    {
        node(podName) {

    def libraries = ['cico-pipeline'           : ['master', 'https://github.com/CentOS/cico-pipeline-library.git'],
                     'contra-lib'              : ['master', 'https://github.com/openshift/contra-lib.git'],
                     'fedora-ci-generic-checks': ['develop', 'https://github.com/tflink/fedora-ci-generic-checks.git']] // should probably pin this to a release

    libraries.each { name, repo ->
        library identifier: "${name}@${repo[0]}",
                retriever: modernSCM([$class: 'GitSCMSource',
                                      remote: repo[1]])

    }

            def jobMeasurement = buildCheckUtils.timedMeasurement()

            def buildResult = null

            // Setting timeout to 8 hours, some packages can take few hours to build in koji
            // and tests can take up to 4 hours to run.
            timeout(time: 8, unit: 'HOURS') {

                env.currentStage = ""

                buildCheckUtils.ciPipeline {
                        // We need to set env.HOME because the openshift slave image
                        // forces this to /home/jenkins and then ~ expands to that
                        // even though id == "root"
                        // See https://github.com/openshift/jenkins/blob/master/slave-base/Dockerfile#L5
                        //
                        // Even the kubernetes plugin will create a pod with containers
                        // whose $HOME env var will be its workingDir
                        // See https://github.com/jenkinsci/kubernetes-plugin/blob/master/src/main/java/org/csanchez/jenkins/plugins/kubernetes/KubernetesLauncher.java#L311
                        //
                        env.HOME = "/root"
                        //
                    try {
                            // Prepare our environment
                        env.currentStage = "prepare-environment"
                        env.messageStage = "running"

                        // set the display prefix
                        currentBuild.displayName = "RPMINSPECT#: ${env.BUILD_NUMBER}"

                        stage(env.currentStage) {

                            buildCheckUtils.handlePipelineStep('stepName': env.currentStage, 'debug': true) {

                                deleteDir()
                                // Parse the CI_MESSAGE and inject it as a var
                                parsedMsg = buildCheckUtils.parseKojiMessage(message: env.CI_MESSAGE, ignoreErrors: false)
                                env.artifact = 'koji-build'

                                // this is needed for setBuildBranch
                                buildCheckUtils.setMessageEnvVars(parsedMsg)
                                env.fed_repo = buildCheckUtils.repoFromRequest(env.request_0)
                                branches = buildCheckUtils.setBuildBranch(env.request_1)
                                env.branch = branches[0]
                                env.fed_branch = branches[1]
                                env.fed_namespace = 'rpms'

                                buildCheckUtils.setDefaultEnvVars()

                                // Gather some info about the node we are running on for diagnostics
                                buildCheckUtils.verifyPod(OPENSHIFT_NAMESPACE, env.NODE_NAME)

                                // Send message org.centos.prod.ci.<artifact>.test.running on fedmsg
                                messageFields = buildCheckUtils.setTestMessageFields("running", artifact, parsedMsg)
                                buildCheckUtils.sendMessage(messageFields['topic'], messageFields['properties'], messageFields['content'])
                            }
                        }


                        env.currentStage = "package-tests"
                        stage(env.currentStage) {
                            buildCheckUtils.handlePipelineStep(stepName: env.currentStage, debug: true) {

                                // Set vars for this specific stage
                                stageVars = buildCheckUtils.setStageEnvVars(env.currentStage)

                                // Prepare to send stage.complete message on failure
                                env.messageStage = 'complete'

                                // parse target envr into env var
                                def json_message = readJSON text: env.CI_MESSAGE
                                print "json_message: " + json_message.toString()
                                env.TARGET_ENVR = "${json_message['msg']['name']}-${json_message['msg']['version']}-${json_message['msg']['release']}"

                                // Run functional tests
                                try {
                                    buildCheckUtils.executeInContainer(containerName: "package-checks",
                                                                            containerScript: "/tmp/run-rpminspect.sh",
                                                                            stageVars: stageVars,
                                                                            stageName: env.currentStage)
                                } catch(e) {
                                    if (fileExists("${WORKSPACE}/${env.currentStage}/logs/test.log")) {
                                        buildResult = 'UNSTABLE'
                                        // set currentBuild.result to update the message status
                                        currentBuild.result = buildResult

                                    } else {
                                        throw e
                                    }
                                }

                                if (fileExists("${WORKSPACE}/${env.currentStage}/logs/results.yml")) {
                                    def test_results = readYaml file: "${WORKSPACE}/${env.currentStage}/logs/results.yml"
                                    def test_failed = false
                                    test_results['results'].each { result ->
                                        // some test case exited with error
                                        // handle this as test failure and not as infra one
                                        if (result.result == "error") {
                                            test_failed = true
                                        }
                                        if (result.result == "fail") {
                                            test_failed = true
                                        }
                                    }
                                    if (test_failed) {
                                        currentBuild.result = 'UNSTABLE'
                                    }
                                }

                            }
                        }

                        buildResult = buildResult ?: 'SUCCESS'

                    } catch (e) {
                        // Set build result
                        buildResult = 'FAILURE'
                        currentBuild.result = buildResult

                        // we don't need to send a 'complete' status message here, will be done in finally block

                        // Send message org.centos.prod.ci.<artifact>.test.error on fedmsg
                        messageFields = buildCheckUtils.setTestMessageFields("error", artifact, parsedMsg)
                        buildCheckUtils.sendMessage(messageFields['topic'], messageFields['properties'], messageFields['content'])

                        // Report the exception
                        echo "Error: Exception from " + env.currentStage + ":"
                        print("The message is: " + e.getMessage())
                        echo e.getMessage()

                        // that turned out to be extremely unhelpful, let's try to get some actual data from the exception
                        org.codehaus.groovy.runtime.StackTraceUtils.printSanitizedStackTrace(e)

                    } finally {
                        currentBuild.result = buildResult
                        buildCheckUtils.getContainerLogsFromPod(OPENSHIFT_NAMESPACE, env.NODE_NAME)

                        // Archive our artifacts
                        if (currentBuild.result == 'SUCCESS') {
                            step([$class: 'ArtifactArchiver', allowEmptyArchive: true, artifacts: '**/logs/**,*.txt,*.groovy,**/job.*,**/*.groovy,**/inventory.*', excludes: '**/job.props,**/job.props.groovy,**/*.example,**/*.qcow2', fingerprint: true])
                        } else {
                            step([$class: 'ArtifactArchiver', allowEmptyArchive: true, artifacts: '**/logs/**,*.txt,*.groovy,**/job.*,**/*.groovy,**/inventory.*,**/*.qcow2', excludes: '**/job.props,**/job.props.groovy,**/*.example,artifacts.ci.centos.org/**,*.qcow2', fingerprint: true])
                        }

                        // Set our message topic, properties, and content
                        messageFields = buildCheckUtils.setTestMessageFields("complete", artifact, parsedMsg)

                        // Send koji-build.test.complete on job completion
                        buildCheckUtils.sendMessage(messageFields['topic'], messageFields['properties'], messageFields['content'])

                    }
                }
            }
        }
    }
}
