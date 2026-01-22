package grails.plugins.mail.oauth.sender

import grails.plugins.mail.MailConfigurationProperties
import grails.plugins.mail.oauth.MailOAuthService
import grails.plugins.mail.oauth.token.OAuthToken
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.mail.MailMessage
import org.springframework.mail.javamail.JavaMailSenderImpl
import javax.mail.Session

@CompileStatic
@Slf4j
class OAuthMailSenderImpl extends JavaMailSenderImpl {

    MailOAuthService mailOAuthService
    Long tenantId
    OAuthMailSenderImpl() {
    }

    OAuthMailSenderImpl(MailConfigurationProperties mailProperties,MailOAuthService mailOAuthService,Long tenantId) {
        this.mailOAuthService = mailOAuthService
        this.tenantId = tenantId
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
        this.session =  Session.getInstance(javaMailProperties)
    }

    @Override
    String getPassword() {
        mailOAuthService.getAccessToken(this.tenantId)?.accessToken
    }

}
