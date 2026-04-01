package grails.plugins.mail.oauth


import grails.plugins.mail.MailConfigurationProperties
import grails.plugins.mail.MailMessageBuilder
import grails.plugins.mail.MailMessageBuilderFactory
import grails.plugins.mail.graph.GraphApiClient
import grails.plugins.mail.graph.sender.GraphMailMessageBuilderFactory
import grails.plugins.mail.graph.sender.GraphMailSenderImpl
import grails.plugins.mail.oauth.sender.OAuthMailSenderImpl
import grails.plugins.mail.oauth.sender.OauthMailMessageBuilderFactory
import grails.plugins.tenant.TenantContextProvider
import grails.plugins.mail.tenant.TenantMailExecutorRegistry
import grails.plugins.tenant.TenantIdContext
import groovy.util.logging.Slf4j
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.env.PropertiesPropertySource
import org.springframework.mail.MailMessage

import java.util.concurrent.ExecutorService

@Slf4j
class TenantMailService {

    TenantMailConfigResolverService tenantMailConfigResolverService
    GraphMailMessageBuilderFactory graphMailMessageBuilderFactory
    OauthMailMessageBuilderFactory oauthMailMessageBuilderFactory
    MailMessageBuilderFactory mailMessageBuilderFactory
    MailOAuthService mailOAuthService
    TenantMailExecutorRegistry tenantMailExecutorRegistry
    TenantGraphClientRegistryService tenantGraphClientRegistryService
    TenantContextProvider tenantContextProvider
    private static final Bindable<MailConfigurationProperties> CONFIG_BINDABLE = Bindable.of(MailConfigurationProperties)


    MailMessage sendMail(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MailMessageBuilder) Closure callable) {
        Long tenantId = tenantContextProvider.getCurrentTenantId()
        sendMailWithTenant(tenantId,callable)
    }

    /**
     * Tenant-aware sendMail API
     */
    MailMessage sendMailWithTenant(Long tenantId,@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MailMessageBuilder) Closure callable) {
        ConfigObject cfg = tenantMailConfigResolverService.resolve(tenantId)

        MailConfigurationProperties props = toMailProperties(cfg)
        MailMessageBuilder builder = resolveBuilder(tenantId,cfg, props)
        callable.delegate = builder
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        TenantIdContext.setTenantId(tenantId)
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
        }else if(cfg?.oAuth?.enabled) {
            log.info("[SMTP OAUTH MAIL] TenantId ={}", tenantId)
            return oauthMailMessageBuilderFactory.createBuilder(props, createOAuthMailSender(props))
        }else{
            return  mailMessageBuilderFactory.createBuilder(props)
        }
    }


    private OAuthMailSenderImpl createOAuthMailSender(MailConfigurationProperties props){
        return new OAuthMailSenderImpl(props, mailOAuthService)
    }

    private GraphMailSenderImpl createGraphMailSender(Long tenantId, ConfigObject cfg) {
        GraphApiClient graphApiClient = tenantGraphClientRegistryService.getClient(tenantId, cfg)
        new GraphMailSenderImpl(
                mailOAuthService,
                graphApiClient,
                MailOAuthUtil.attachmentMax(),
                MailOAuthUtil.isDaemon())
    }

    MailConfigurationProperties toMailProperties(ConfigObject config) {
        def propertySource = new PropertiesPropertySource('mailProperties', config.toProperties())
        def configurationPropertySources = ConfigurationPropertySources.from(propertySource)
        def binder = new Binder(configurationPropertySources)
        return binder.bind("", CONFIG_BINDABLE).get()
    }
}
