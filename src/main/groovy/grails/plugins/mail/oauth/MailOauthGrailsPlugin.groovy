package grails.plugins.mail.oauth

import grails.plugins.*
import grails.plugins.mail.oauth.sender.OAuthMailSenderImpl

import grails.plugins.mail.oauth.token.MemoryTokenStore

class MailOauthGrailsPlugin extends Plugin {

    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "3.0.14 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def title = "Mail Oauth" // Headline display name of the plugin
    def author = "Sachin Verma"
    def authorEmail = "sachin.verma@rxlogix.com"
    def description = '''\
This plugin has been developed for supporting Microsoft OAuth based SMTP protocol.
'''
    def profiles = ['web']

    List loadAfter = ['mail']

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/mail-oauth"

    Integer mailConfigHash
    ConfigObject mailConfig

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
//    def license = "APACHE"

    // Details of company behind the plugin (if there is one)
//    def organization = [ name: "My Company", url: "http://www.my-company.com/" ]

    // Any additional developers beyond the author specified above.
//    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

    // Location of the plugin's issue tracker.
//    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]

    // Online location of the plugin's browseable source code.
//    def scm = [ url: "http://svn.codehaus.org/grails-plugins/" ]

    Closure doWithSpring() {
        { ->
            mailConfig = grailsApplication.config.grails.mail
            if (!mailConfig.oAuth.enabled) {
                return
            }
            println "Loading mail-oAuth configuration"
            mailConfigHash = mailConfig.hashCode()

            configureMailOAuthSender(delegate, mailConfig)
            tokenStore(MemoryTokenStore)
        }
    }

    void onConfigChange(Map<String, Object> event) {
        ConfigObject newMailConfig = event.source.grails.mail
        if (!newMailConfig.oAuth.enabled) {
            return
        }
        Integer newMailConfigHash = newMailConfig.hashCode()

        if (newMailConfigHash != mailConfigHash) {
            event.ctx.removeBeanDefinition("mailSender")
            mailConfig = newMailConfig
            mailConfigHash = newMailConfigHash
            beans {
                configureMailOAuthSender(delegate, mailConfig)
            }
        }
    }


    private def configureMailOAuthSender(builder, config) {
        builder.with {
            mailSender(OAuthMailSenderImpl) {
                if (config.host) {
                    host = config.host
                } else if (!config.jndiName) {
                    def envHost = System.getenv()['SMTP_HOST']
                    if (envHost) {
                        host = envHost
                    } else {
                        host = "localhost"
                    }
                }

                if (config.encoding) {
                    defaultEncoding = config.encoding
                } else if (!config.jndiName) {
                    defaultEncoding = "utf-8"
                }

                if (config.jndiName)
                    session = ref('mailSession')
                if (config.port)
                    port = config.port
                if (config.username)
                    username = config.username
                if (config.password)
                    password = config.password
                if (config.protocol)
                    protocol = config.protocol
                if (config.props instanceof Map && config.props)
                    javaMailProperties = config.props.toFlatConfig()
                //Required for XOAUTh2 support
                if (javaMailProperties instanceof Map) {
                    javaMailProperties.put('mail.smtp.auth', 'true')
                    javaMailProperties.put('mail.smtp.auth.mechanisms', 'XOAUTH2')
                } else {
                    javaMailProperties.setProperty('mail.smtp.auth', 'true')
                    javaMailProperties.setProperty('mail.smtp.auth.mechanisms', 'XOAUTH2')
                }
                mailOAuthService = ref('mailOAuthService')
            }
        }

    }
}