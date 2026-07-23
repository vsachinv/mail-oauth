package grails.plugins.mail.oauth

import com.microsoft.graph.models.Message
import grails.plugins.mail.MailMessageBuilder
import grails.plugins.mail.MailMessageBuilderFactory
import grails.plugins.mail.graph.GraphApiClient
import grails.plugins.mail.graph.GraphMessage
import grails.plugins.mail.graph.sender.GraphMailMessageBuilderFactory
import grails.plugins.mail.oauth.sender.OauthMailMessageBuilderFactory
import grails.plugins.mail.tenant.TenantMailExecutorRegistry
import grails.plugins.tenant.TenantContextProvider
import grails.plugins.tenant.TenantIdContext
import grails.testing.services.ServiceUnitTest
import org.springframework.mail.MailMessage
import org.springframework.mail.MailSendException
import spock.lang.Specification

import java.util.concurrent.ExecutorService

class TenantMailServiceSpec extends Specification implements ServiceUnitTest<TenantMailService> {

    TenantContextProvider tenantContext
    TenantMailConfigResolverService configResolver
    GraphMailMessageBuilderFactory graphFactory
    OauthMailMessageBuilderFactory oauthFactory
    MailMessageBuilderFactory plainFactory
    TenantGraphClientRegistryService graphClientRegistry
    TenantMailExecutorRegistry executorRegistry
    MailOAuthService mailOAuthService
    MailMessageBuilder builder
    ExecutorService executor

    def setup() {
        tenantContext = Mock(TenantContextProvider)
        configResolver = Mock(TenantMailConfigResolverService)
        graphFactory = Mock(GraphMailMessageBuilderFactory)
        oauthFactory = Mock(OauthMailMessageBuilderFactory)
        plainFactory = Mock(MailMessageBuilderFactory)
        graphClientRegistry = Mock(TenantGraphClientRegistryService)
        executorRegistry = Mock(TenantMailExecutorRegistry)
        mailOAuthService = Mock(MailOAuthService)
        builder = Mock(MailMessageBuilder)
        executor = Mock(ExecutorService)

        service.tenantContextProvider = tenantContext
        service.tenantMailConfigResolverService = configResolver
        service.graphMailMessageBuilderFactory = graphFactory
        service.oauthMailMessageBuilderFactory = oauthFactory
        service.mailMessageBuilderFactory = plainFactory
        service.tenantGraphClientRegistryService = graphClientRegistry
        service.tenantMailExecutorRegistry = executorRegistry
        service.mailOAuthService = mailOAuthService
    }

    /** A resolved tenant config toggling the OAuth / Graph flags; host+username are needed so the
     *  Spring binder in toMailProperties() produces a non-empty MailConfigurationProperties. */
    private static ConfigObject config(boolean oauthEnabled, boolean graphEnabled) {
        ConfigObject cfg = new ConfigObject()
        cfg.host = 'localhost'
        cfg.username = 'sender@rxlogix.com'
        cfg.oAuth.enabled = oauthEnabled
        cfg.oAuth.graph.enabled = graphEnabled
        cfg
    }

    def cleanup() {
        TenantIdContext.clear()
    }

    private sendSample() {
        service.sendMail {
            multipart false
            to 'sachin.verma@rxlogix.com'
            subject 'test email'
            body 'test body'
        }
    }

    void "sendMail routes through the Graph builder and returns a Graph Message when OAuth + Graph are enabled"() {
        given:
        Long tenantId = 1L
        GraphMessage graphMessage = new GraphMessage(subject: 'test')

        when:
        def result = sendSample()

        then:
        1 * tenantContext.getCurrentTenantId() >> tenantId
        _ * configResolver.resolve(tenantId) >> config(true, true)
        1 * graphClientRegistry.getClient(tenantId, _) >> Mock(GraphApiClient)
        1 * graphFactory.createBuilder(_, _) >> builder
        1 * executorRegistry.executorFor(tenantId, _) >> executor
        1 * builder.sendMessage(executor) >> graphMessage
        0 * oauthFactory._
        0 * plainFactory._

        and:
        result instanceof Message
    }

    void "sendMail routes through the OAuth-SMTP builder when OAuth is enabled but Graph is not"() {
        given:
        Long tenantId = 2L

        when:
        def result = sendSample()

        then:
        1 * tenantContext.getCurrentTenantId() >> tenantId
        _ * configResolver.resolve(tenantId) >> config(true, false)
        1 * oauthFactory.createBuilder(_, _) >> builder
        1 * executorRegistry.executorFor(tenantId, _) >> executor
        1 * builder.sendMessage(executor) >> Mock(MailMessage)
        0 * graphFactory._
        0 * graphClientRegistry._
        0 * plainFactory._

        and:
        result != null
    }

    void "sendMail falls back to the plain mail builder when OAuth is disabled"() {
        given:
        Long tenantId = 3L

        when:
        def result = sendSample()

        then:
        1 * tenantContext.getCurrentTenantId() >> tenantId
        _ * configResolver.resolve(tenantId) >> config(false, false)
        1 * plainFactory.createBuilder(_) >> builder
        1 * executorRegistry.executorFor(tenantId, _) >> executor
        1 * builder.sendMessage(executor) >> Mock(MailMessage)
        0 * graphFactory._
        0 * oauthFactory._

        and:
        result != null
    }

    void "sendMail propagates the failure when no configuration can be resolved for the tenant"() {
        given:
        Long tenantId = 4L

        when:
        sendSample()

        then:
        1 * tenantContext.getCurrentTenantId() >> tenantId
        1 * configResolver.resolve(tenantId) >> { throw new IllegalStateException("Mail configuration is not found for tenant: 4") }
        thrown(IllegalStateException)

        and: "no builder is ever created or dispatched"
        0 * graphFactory._
        0 * oauthFactory._
        0 * plainFactory._
        0 * builder.sendMessage(_)
    }

    void "sendMail restores the caller thread's previous tenant id after dispatch (no bleed)"() {
        given:
        Long tenantId = 7L
        TenantIdContext.clear()   // caller thread starts with no tenant

        when:
        sendSample()

        then:
        1 * tenantContext.getCurrentTenantId() >> tenantId
        _ * configResolver.resolve(tenantId) >> config(true, true)
        1 * graphClientRegistry.getClient(tenantId, _) >> Mock(GraphApiClient)
        1 * graphFactory.createBuilder(_, _) >> builder
        1 * executorRegistry.executorFor(tenantId, _) >> executor
        1 * builder.sendMessage(executor) >> new GraphMessage(subject: 't')

        and: "the thread-local is not left holding the tenant id"
        TenantIdContext.getTenantId() == null
    }

    void "sendMail propagates a MailSendException raised while dispatching the message"() {
        given:
        Long tenantId = 5L

        when:
        sendSample()

        then:
        1 * tenantContext.getCurrentTenantId() >> tenantId
        _ * configResolver.resolve(tenantId) >> config(true, true)
        1 * graphClientRegistry.getClient(tenantId, _) >> Mock(GraphApiClient)
        1 * graphFactory.createBuilder(_, _) >> builder
        1 * executorRegistry.executorFor(tenantId, _) >> executor
        1 * builder.sendMessage(executor) >> { throw new MailSendException("mail server connection failed") }
        thrown(MailSendException)
    }
}
