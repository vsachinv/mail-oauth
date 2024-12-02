package grails.plugins.mail.oauth.sender

import grails.plugins.mail.MailConfigurationProperties
import grails.plugins.mail.oauth.MailOAuthService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.mail.javamail.JavaMailSenderImpl
import javax.mail.Session

@CompileStatic
@Slf4j
class OAuthMailSenderImpl extends JavaMailSenderImpl {

    MailOAuthService mailOAuthService

    OAuthMailSenderImpl() {
    }

    OAuthMailSenderImpl(Session mailSession,
                        MailConfigurationProperties mailProperties) {
        if (mailProperties.host) {
            this.host = mailProperties.host
        } else if (!mailProperties.jndiName) {
            def envHost = System.getenv()['SMTP_HOST']
            if (envHost) {
                this.host = envHost
            } else {
                this.host = 'localhost'
            }
        }
        if (mailProperties.encoding) {
            this.defaultEncoding = mailProperties.encoding
        } else if (!mailProperties.jndiName) {
            this.defaultEncoding = 'utf-8'
        }
        if (mailProperties.port) {
            this.port = mailProperties.port
        }
        if (mailProperties.username) {
            this.username = mailProperties.username
        }
        if (mailProperties.password) {
            this.password = mailProperties.password
        }
        if (mailProperties.protocol) {
            this.protocol = mailProperties.protocol
        }
        if (mailProperties.props) {
            this.javaMailProperties = mailProperties.props
        } else {
            this.javaMailProperties = new Properties()
        }
        this.javaMailProperties.setProperty('mail.smtp.auth', 'true')
        this.javaMailProperties.setProperty('mail.smtp.auth.mechanisms', 'XOAUTH2')
        if (mailSession != null) {
            this.session = mailSession
        }
    }

    @Override
    String getPassword() {
        mailOAuthService.getAccessToken()?.accessToken
    }

}
