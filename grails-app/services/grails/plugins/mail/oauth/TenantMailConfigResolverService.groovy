package grails.plugins.mail.oauth

import grails.core.GrailsApplication
import grails.gorm.services.Service
import groovy.util.logging.Slf4j

@Service
@Slf4j
class TenantMailConfigResolverService {

    GrailsApplication grailsApplication

    ConfigObject resolve(Long tenantId) {
        if (!tenantId) {
            throw new IllegalArgumentException("tenantId is required")
        }
        // Common config (Organization level)
        ConfigObject commonCfg = (grailsApplication.config?.grails?.mail ?: new ConfigObject()) as ConfigObject

        // Tenant config (Tenant level)
        ConfigObject tenantCfg =
                (grailsApplication.config.get(MailOAuthUtil.TENANT_PREFIX + "$tenantId")?.grails?.mail ?: new ConfigObject()) as ConfigObject

        if(!tenantCfg){
            log.error("Mail configuration is not found for tenant: ${tenantId}")
            throw new IllegalStateException(
                    "Mail configuration is not found for tenant: ${tenantId}"
            )
        }
        // check UserName , ClientId and Secret Value should not empty at tenantId
        if(!isTenantOAuthConfigValid(tenantCfg)){
            log.error("Mail configuration (username, clientId, secret_val) is not found for tenant: ${tenantId}")
            throw new IllegalStateException(
                    "Mail configuration is not found for tenant: ${tenantId}"
            )
        }

        // Merge: common first, then tenant overrides
        ConfigObject merged = new ConfigObject()
        merged.merge(commonCfg)
        merged.merge(tenantCfg)

        return merged
    }

    boolean isTenantOAuthConfigValid(ConfigObject tenantCfg) {
        tenantCfg?.username &&
                tenantCfg?.oAuth?.client_id &&
                tenantCfg?.oAuth?.secret_val
    }
}
