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

        // Tenant config (Tenant level)
        ConfigObject tenantCfg =
                (grailsApplication.config.get(MailOAuthUtil.TENANT_PREFIX + "$tenantId")?.grails?.mail ?: new ConfigObject()) as ConfigObject

        if(!tenantCfg){
            log.error("Mail configuration is not found for tenant: ${tenantId}")
            throw new IllegalStateException(
                    "Mail configuration is not found for tenant: ${tenantId}"
            )
        }

        ConfigObject merged = new ConfigObject()
        merged.merge(tenantCfg)
        return merged
    }

}
