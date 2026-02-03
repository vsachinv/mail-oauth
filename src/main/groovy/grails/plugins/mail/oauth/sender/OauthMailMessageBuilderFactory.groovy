package grails.plugins.mail.oauth.sender

import grails.plugins.mail.MailConfigurationProperties
import grails.plugins.mail.MailMessageBuilder
import grails.plugins.mail.MailMessageBuilderFactory
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.mail.MailSender

@Slf4j
@CompileStatic
class OauthMailMessageBuilderFactory extends  MailMessageBuilderFactory {
    MailMessageBuilder createBuilder(MailConfigurationProperties properties, MailSender mailSender) {
        new MailMessageBuilder(mailSender, properties, mailMessageContentRenderer)
    }

}
