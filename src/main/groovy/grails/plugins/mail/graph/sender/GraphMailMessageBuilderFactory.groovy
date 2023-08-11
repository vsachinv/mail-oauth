package grails.plugins.mail.graph.sender

import grails.config.Config
import grails.plugins.mail.MailMessageBuilder
import grails.plugins.mail.MailMessageBuilderFactory
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.web.mime.DefaultMimeUtility

@Slf4j
@CompileStatic
class GraphMailMessageBuilderFactory extends MailMessageBuilderFactory {

    DefaultMimeUtility grailsMimeUtility

    MailMessageBuilder createBuilder(Config config) {
        new GraphMailMessageBuilder(mailSender, config, mailMessageContentRenderer, grailsMimeUtility)
    }

}