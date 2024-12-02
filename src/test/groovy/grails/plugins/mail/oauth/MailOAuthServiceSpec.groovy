package grails.plugins.mail.oauth

import grails.plugins.mail.MailMessageBuilder
import grails.plugins.mail.MailService
import grails.plugins.mail.graph.GraphMessage
import grails.testing.services.ServiceUnitTest
import org.grails.spring.beans.factory.InstanceFactoryBean
import org.springframework.mail.MailMessage
import spock.lang.Specification

class MailOAuthServiceSpec extends Specification implements ServiceUnitTest<MailOAuthService> {

    def setupSpec() {
        defineBeans {
            mailService(InstanceFactoryBean, new MailService() {
                @Override
                MailMessage sendMail(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MailMessageBuilder) Closure dsl) {
                    return new GraphMessage(subject: 'test')
                }
            })
        }
    }

    void setup() {
    }

    void cleanup() {
    }

    void "test sendMail"() {
        expect:
        (service.sendMail {
            to 'sachin.verma@rxlogix.com'
            subject 'test1'
            text 'test1'
        }) instanceof com.microsoft.graph.models.Message
    }
}
