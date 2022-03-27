package grails.plugins.mail.oauth.sender

import grails.plugins.mail.oauth.MailOAuthService
import groovy.transform.CompileStatic
import org.springframework.mail.javamail.JavaMailSenderImpl

@CompileStatic
class OAuthMailSenderImpl extends JavaMailSenderImpl {

    MailOAuthService mailOAuthService

    OAuthMailSenderImpl() {
    }

    @Override
    String getPassword() {
        mailOAuthService.getAccessToken()
    }

}
