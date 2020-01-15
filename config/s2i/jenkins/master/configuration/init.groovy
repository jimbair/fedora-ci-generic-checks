import java.util.logging.Logger
import jenkins.security.s2m.*
import hudson.model.*
import hudson.security.*
import jenkins.model.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.jenkins.plugins.sshcredentials.impl.*
import hudson.slaves.*
import hudson.slaves.EnvironmentVariablesNodeProperty.Entry
import hudson.plugins.sshslaves.*
import hudson.plugins.sshslaves.verifiers.*
import hudson.plugins.openid.*
import com.redhat.jenkins.plugins.ci.*
import com.redhat.jenkins.plugins.ci.messaging.*

def logger = Logger.getLogger("")
//logger.info("Disabling CLI over remoting")
//jenkins.CLI.get().setEnabled(false);
logger.info("Enable Slave -> Master Access Control")
Jenkins.instance.injector.getInstance(AdminWhitelistRule.class).setMasterKillSwitch(false)
Jenkins.instance.save()

global_domain = Domain.global()

def loginRealm = new OpenIdSsoSecurityRealm("https://id.fedoraproject.org/")
Jenkins.instance.setSecurityRealm(loginRealm)

def matrix_auth = new ProjectMatrixAuthorizationStrategy()
matrix_auth.add(hudson.model.Hudson.READ,'anonymous')
matrix_auth.add(hudson.model.Item.DISCOVER,'anonymous')
matrix_auth.add(hudson.model.Item.READ,'anonymous')
matrix_auth.add(hudson.model.Hudson.ADMINISTER, 'sysadmin-qa')

Jenkins.instance.setAuthorizationStrategy(matrix_auth)

logger.info("Setup fedora-fedmsg Messaging Provider")
FedMsgMessagingProvider fedmsg = new FedMsgMessagingProvider("fedora-fedmsg", "tcp://hub.fedoraproject.org:9940", "tcp://172.19.4.24:9941", "org.fedoraproject");
GlobalCIConfiguration.get().addMessageProvider(fedmsg)

logger.info("Setup fedora-fedmsg-stage Messaging Provider")
FedMsgMessagingProvider fedmsgStage = new FedMsgMessagingProvider("fedora-fedmsg-stage", "tcp://stg.fedoraproject.org:9940", "tcp://172.19.4.36:9941", "org.fedoraproject");
GlobalCIConfiguration.get().addMessageProvider(fedmsgStage)

// attempting to set script approvals
def approval_signatures = [
    "method java.lang.Class getSuperclass",
    "method java.lang.Class isAssignableFrom java.lang.Class",
    "method net.sf.json.JSONObject has java.lang.String",
    "staticMethod java.lang.Class forName java.lang.String",
    "staticMethod java.time.Instant now",
    "staticMethod org.codehaus.groovy.runtime.StackTraceUtils printSanitizedStackTrace java.lang.Throwable",
    "java.io.PrintWriter",
    "staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods getAt java.util.List java.util.Collection",
    "new java.lang.Exception java.lang.String java.lang.Throwable",
    "new java.lang.Exception java.lang.String java.lang.Throwable"]

def approval = org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval.get()
approval_signatures.each {signature -> approval.approveSignature(signature) }


