import jenkins.model.*
import hudson.tasks.*;

def getEnvironmentVariables() {
    def envFilePath = System.getenv('JENKINS_ENV')
    Properties properties = new Properties()
    File propsFile = new File(envFilePath)
    properties.load(propsFile.newDataInputStream())
    return properties
}

def configureEmailPublisher() {
    def properties = getEnvironmentVariables()
    def smtpServer = properties.getProperty('CP_NOTIFIER_SMTP_SERVER_HOST')
    def smtpPort = properties.getProperty('CP_NOTIFIER_SMTP_SERVER_PORT')
    def smtpUser = properties.getProperty('CP_NOTIFIER_SMTP_USER')
    def smtpPassword = properties.getProperty('CP_NOTIFIER_SMTP_PASS')

    def jenkinsLocationConfiguration = JenkinsLocationConfiguration.get()
    jenkinsLocationConfiguration.setAdminAddress(smtpUser)
    jenkinsLocationConfiguration.save()

    def instance = Jenkins.getInstance()
    def emailDescriptor = instance.getDescriptor("hudson.tasks.Mailer")

    // Set the SMTP settings for email server
    emailDescriptor.setSmtpHost(smtpServer)
    emailDescriptor.setSmtpPort(smtpPort)
    emailDescriptor.setSmtpAuth(smtpUser, smtpPassword)
    emailDescriptor.setUseSsl(false)
    emailDescriptor.setCharset("UTF-8")
    instance.save()

    //ExtendedEmail
    def emailExtPluginExtension = Jenkins.instance
            .getExtensionList(hudson.plugins.emailext.ExtendedEmailPublisherDescriptor.class)[0]
    emailExtPluginExtension.upgradeFromMailer()
    emailExtPluginExtension.defaultContentType = "text/html"
    emailExtPluginExtension.defaultBody = '${SCRIPT, template="mail-html.template"}'
    emailExtPluginExtension.save()
}

configureEmailPublisher()
