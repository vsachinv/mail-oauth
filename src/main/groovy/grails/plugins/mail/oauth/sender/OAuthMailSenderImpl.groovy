package grails.plugins.mail.oauth.sender

import grails.plugins.mail.oauth.MailOAuthService
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.mail.javamail.JavaMailSenderImpl

@CompileStatic
@Slf4j
class OAuthMailSenderImpl extends JavaMailSenderImpl {

    MailOAuthService mailOAuthService

    OAuthMailSenderImpl() {
    }

    @Override
    String getPassword() {
        mailOAuthService.getAccessToken()?.accessToken
    }

}
