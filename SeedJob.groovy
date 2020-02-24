pipelineJob('fedora-messaging-trigger'){
    triggers{
        ciBuildTrigger{
            noSquash(true)
            providers {
                providerDataEnvelope {
                    providerData {
                rabbitMQSubscriber{
                    name("FedoraMessaging")
                    overrides {
                        topic("org.fedoraproject.prod.buildsys.build.state.change")
                    }
                    checks {
                        msgCheck {
                            field("new")
                            expectedValue("1|CLOSED")
                        }
                        msgCheck {
                            field("release")
                            expectedValue(".*fc.*")
                        }
                        msgCheck {
                            field("instance")
                            expectedValue("primary")
                        }
                    }
                }
                    }}
            }
        }
    }
    definition {
        cps {
            script(readFileFromWorkspace("src/jobs/trigger.groovy"))
        }
    }
}

pipelineJob('fedora-rpminspect'){

    description 'Job to run checks on Fedora builds'

    // default so we don't need to wait around for builds to happen
    def CANNED_CI_MESSAGE = '{"deliveryTag":584,"msg":{"attribute":"state","build_id":1430989,"epoch":null,"instance":"primary","name":"file-roller","new":1,"old":0,"owner":"kalev","release":"1.fc32","request":["git+https://src.fedoraproject.org/rpms/file-roller.git#e00aa0590662351c540d0a2782382ee45fa6bba5","f32-build-side-18035",{"skip_tag":true}],"task_id":40809920,"version":"3.35.1"},"msg_id":"6b5854b1-550e-4bf7-b145-fa6c406903e1","timestamp":1579599505254,"topic":"org.fedoraproject.prod.buildsys.build.state.change"}'


    // Audit file for all messages sent.
    msgAuditFile = "messages/message-audit.json"

    // Number of times to keep retrying to make sure message is ingested
    // by datagrepper
    fedmsgRetryCount = 120



    parameters{
        stringParam('CI_MESSAGE', CANNED_CI_MESSAGE, 'fedora-fedmsg')
        // This is for apps.ci.centos.org
        stringParam('DOCKER_REPO_URL', '172.30.254.79:5000', 'Docker Repo URL')
        stringParam('OPENSHIFT_NAMESPACE', 'fedora-package-checks', 'OpenShift Namespace')
        stringParam('OPENSHIFT_SERVICE_ACCOUNT', 'fedora-check-jenkins', 'OpenShift Service Account')
        stringParam('SLAVE_TAG', 'latest', 'tag for slave image')
        stringParam('FEDORACI_RUNNER_TAG', 'latest', 'tag for worker image')
        stringParam('RUNNING_ENVIRONMENT', 'prod', 'Which environment are we running in')
    }

    definition {
        cps {
            script(readFileFromWorkspace("src/jobs/RpminspectBasic.groovy"))
        }
    }
}
