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
        ConfigObject cfg = grailsApplication.config.get(MailOAuthUtil.TENANT_PREFIX+"$tenantId")?.grails?.mail
        return cfg
    }
}
