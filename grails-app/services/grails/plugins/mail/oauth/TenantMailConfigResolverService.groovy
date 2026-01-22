package grails.plugins.mail.oauth

import grails.core.GrailsApplication
import grails.gorm.services.Service

@Service
class TenantMailConfigResolverService {

    GrailsApplication grailsApplication

    ConfigObject resolve(Long tenantId) {
        if (!tenantId) {
            throw new IllegalArgumentException("tenantId is required")
        }
        def cfg = grailsApplication.config[tenantId]?.grails?.mail

        if (!cfg) {
            throw new IllegalStateException(
                    "Mail configuration not found for tenant: ${tenantId}"
            )
        }

        cfg
    }
}
