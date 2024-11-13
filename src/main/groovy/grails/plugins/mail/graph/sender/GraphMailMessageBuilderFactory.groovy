package grails.plugins.mail.graph.sender

import grails.plugins.mail.MailConfigurationProperties
import grails.plugins.mail.MailMessageBuilder
import grails.plugins.mail.MailMessageBuilderFactory
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.web.mime.DefaultMimeUtility

@Slf4j
@CompileStatic
class GraphMailMessageBuilderFactory extends MailMessageBuilderFactory {

    DefaultMimeUtility grailsMimeUtility

    MailMessageBuilder createBuilder(MailConfigurationProperties properties) {
        new GraphMailMessageBuilder(mailSender, properties, mailMessageContentRenderer, grailsMimeUtility)
    }

}