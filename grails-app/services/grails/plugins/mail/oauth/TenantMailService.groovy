package grails.plugins.mail.oauth


import grails.plugins.mail.MailConfigurationProperties
import grails.plugins.mail.MailMessageBuilder
import grails.plugins.mail.MailMessageBuilderFactory
import grails.plugins.mail.graph.GraphApiClient
import grails.plugins.mail.graph.sender.GraphMailMessageBuilderFactory
import grails.plugins.mail.graph.sender.GraphMailSenderImpl
import grails.plugins.mail.graph.token.TokenBasedAuthCredential
import grails.plugins.mail.oauth.sender.OAuthMailSenderImpl
import grails.plugins.mail.oauth.sender.OauthMailMessageBuilderFactory
import grails.plugins.mail.tenant.TenantMailExecutorRegistry
import groovy.util.logging.Slf4j
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.env.PropertiesPropertySource
import java.util.concurrent.ExecutorService

@Slf4j
class TenantMailService {

    TenantMailConfigResolverService tenantMailConfigResolverService
    MailMessageBuilderFactory mailMessageBuilderFactory
    GraphMailMessageBuilderFactory graphMailMessageBuilderFactory
    OauthMailMessageBuilderFactory oauthMailMessageBuilderFactory
    MailOAuthService mailOAuthService
    TenantMailExecutorRegistry tenantMailExecutorRegistry
    private static final Bindable<MailConfigurationProperties> CONFIG_BINDABLE = Bindable.of(MailConfigurationProperties)

    /**
     * Tenant-aware sendMail API
     */
    void sendMail(Long tenantId,
                  @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MailMessageBuilder)
                          Closure callable) {

        ConfigObject cfg = tenantMailConfigResolverService.resolve(tenantId)
        if (!cfg) {
            log.error("Mail configuration not found for tenant: ${tenantId}")
            throw new IllegalStateException(
                    "Mail configuration not found for tenant: ${tenantId}"
            )
        }

        MailConfigurationProperties props = toMailProperties(cfg)
        MailMessageBuilder builder = resolveBuilder(tenantId,cfg, props)
        callable.delegate = builder
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.call(builder)
        ExecutorService executor =
                tenantMailExecutorRegistry.executorFor(tenantId, props.poolSize)
        builder.sendMessage(executor)
    }


    private MailMessageBuilder resolveBuilder(Long tenantId, ConfigObject cfg,
                                              MailConfigurationProperties props) {

        if (cfg?.oAuth?.enabled && cfg?.oAuth?.graph?.enabled) {
            log.info("GRAPH MAIL TenantId = {}",tenantId)
            return graphMailMessageBuilderFactory.createBuilder(props,createGraphMailSender(tenantId, cfg))
        }
        log.info("[SMTP OAUTH MAIL] TenantId ={}",tenantId)
        return  oauthMailMessageBuilderFactory.createBuilder(props,createOAuthMailSender(tenantId,props))
    }


    private OAuthMailSenderImpl createOAuthMailSender(Long tenantId, MailConfigurationProperties props){
        return new OAuthMailSenderImpl(props, mailOAuthService, tenantId)
    }

    private GraphMailSenderImpl createGraphMailSender(Long tenantId, ConfigObject cfg) {
        Integer  maxAttachmentSizeInMB = cfg?.oAuth?.graph?.attachmentMax ?: MailOAuthUtil.MAX_ATTACHMENT_SIZE_IN_MB
        Boolean   daemon = cfg?.oAuth?.daemon ?: false as Boolean
        GraphApiClient graphApiClient = createGraphApiClient(tenantId, cfg)
        new GraphMailSenderImpl(
                mailOAuthService,
                graphApiClient,
                maxAttachmentSizeInMB,
                daemon,
                tenantId
        )
    }

    private GraphApiClient createGraphApiClient(Long tenantId, ConfigObject cfg) {
        String scopes =  cfg.oAuth.api_scope
        Boolean debug =  cfg.oAuth.debug ?: false
        Long connectTimeout =  cfg.oAuth.graph.http.connectTimeout ?: 30L
        Long writeTimeout = cfg.oAuth.graph.http.writeTimeout ?: 600L
        Long readTimeout =  cfg.oAuth.graph.http.readTimeout ?: 600L
        new GraphApiClient(new TokenBasedAuthCredential(tenantId, mailOAuthService),
                scopes, debug, connectTimeout, writeTimeout, readTimeout
        )
    }

    MailConfigurationProperties toMailProperties(ConfigObject config) {
        def propertySource = new PropertiesPropertySource('mailProperties', config.toProperties())
        def configurationPropertySources = ConfigurationPropertySources.from(propertySource)
        def binder = new Binder(configurationPropertySources)
        return binder.bind("", CONFIG_BINDABLE).get()
    }
}
