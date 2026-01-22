package grails.plugins.mail.oauth

import grails.plugins.mail.MailConfigurationProperties
import grails.plugins.mail.MailMessageBuilder
import grails.plugins.mail.MailMessageBuilderFactory
import grails.plugins.mail.MailService
import grails.plugins.mail.graph.GraphApiClient
import grails.plugins.mail.graph.sender.GraphMailMessageBuilderFactory
import grails.plugins.mail.graph.sender.GraphMailSenderImpl
import grails.plugins.mail.graph.token.TokenBasedAuthCredential
import grails.plugins.mail.oauth.sender.OAuthMailSenderImpl
import grails.plugins.mail.tenant.TenantMailExecutorRegistry
import groovy.util.logging.Slf4j
import org.springframework.mail.MailSender

import java.util.concurrent.ExecutorService

@Slf4j
class TenantMailService {

    TenantMailConfigResolverService tenantMailConfigResolverService
    MailMessageBuilderFactory mailMessageBuilderFactory
    GraphMailMessageBuilderFactory graphMailMessageBuilderFactory
    MailOAuthService mailOAuthService
    TenantMailExecutorRegistry tenantMailExecutorRegistry

    /**
     * Tenant-aware sendMail API
     */
    void sendMail(Long tenantId,
                  @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MailMessageBuilder)
                          Closure callable) {

        def cfg = tenantMailConfigResolverService.resolve(tenantId)

        MailConfigurationProperties props =
                MailService.toMailProperties(cfg)

        MailMessageBuilder builder = resolveBuilder(cfg, props)

        callable.delegate = builder
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.call(builder)

        builder.mailSender = createMailSender(tenantId, cfg, props)

        ExecutorService executor =
                tenantMailExecutorRegistry.executorFor(tenantId, props.poolSize)

        builder.sendMessage(executor)
    }

    // ------------------------------------------------------------------

    private MailMessageBuilder resolveBuilder(def cfg,
                                              MailConfigurationProperties props) {
        if (cfg?.oAuth?.enabled && cfg?.oAuth?.graph?.enabled) {
            log.debug("[MAIL] Tenant={} → GRAPH", cfg.tenantId)
            return graphMailMessageBuilderFactory.createBuilder(props)
        }
        log.debug("[MAIL] Tenant={} → SMTP", cfg.tenantId)
        return mailMessageBuilderFactory.createBuilder(props)
    }

    private MailSender createMailSender(Long tenantId,
                                        def cfg,
                                        MailConfigurationProperties props) {

        if (cfg?.oAuth?.enabled && cfg?.oAuth?.graph?.enabled) {
            return createGraphMailSender(tenantId, cfg)
        }

        if (cfg?.oAuth?.enabled) {
            return new OAuthMailSenderImpl(props, tenantId)
        }

        return null
    }

    private GraphMailSenderImpl createGraphMailSender(Long tenantId, def cfg) {
        new GraphMailSenderImpl(
                mailOAuthService,
                createGraphApiClient(tenantId, cfg),
                cfg.oAuth.graph.attachmentMax,
                cfg.oAuth.daemon,
                tenantId
        )
    }

    private GraphApiClient createGraphApiClient(Long tenantId, def cfg) {
        new GraphApiClient(
                new TokenBasedAuthCredential(tenantId, mailOAuthService),
                cfg.oAuth.api_scope,
                cfg.oAuth.debug ?: false,
                cfg.oAuth.graph.http.connectTimeout ?: 30L,
                cfg.oAuth.graph.http.writeTimeout ?: 600L,
                cfg.oAuth.graph.http.readTimeout ?: 600L
        )
    }
}
