package grails.plugins.mail.oauth

import grails.core.GrailsApplication
import groovy.util.logging.Slf4j

@Slf4j
class TenantMailConfigResolverService {

    GrailsApplication grailsApplication

    ConfigObject resolve(Long tenantId) {
        if (!tenantId) {
            throw new IllegalArgumentException("tenantId is required")
        }
        Long defaultTenantId = grailsApplication?.config?.getProperty("DEFAULT_TENANT_ID", Long)
        ConfigObject resolvedCfg
        if (tenantId == defaultTenantId) {
            // Organization level configuration (existing email config)
            resolvedCfg = (grailsApplication?.config?.grails?.mail ?: new ConfigObject()) as ConfigObject
        } else {
            // Tenant specific configuration
            resolvedCfg = (
                    grailsApplication?.config
                            ?.get("${MailOAuthUtil.TENANT_PREFIX}${tenantId}")
                            ?.grails?.mail
                            ?: new ConfigObject()
            ) as ConfigObject
        }

        if (!resolvedCfg || resolvedCfg.isEmpty()) {
            log.error("Mail configuration is not found for tenant: ${tenantId}")
            throw new IllegalStateException(
                    "Mail configuration is not found for tenant: ${tenantId}"
            )
        }

        // Build defaults from the top-level grails.mail config
        ConfigObject defaultCfg = new ConfigObject()
        def globalMailConfig = grailsApplication?.config?.grails?.mail

        if (globalMailConfig?.host) {
            defaultCfg.host = globalMailConfig.host
        }
        if (globalMailConfig?.port) {
            defaultCfg.port = globalMailConfig.port
        }
        if (globalMailConfig?.props) {
            defaultCfg.props = globalMailConfig.props
        }

        // Merge: defaults first, then tenant/org config on top (tenant values win)
        ConfigObject merged = new ConfigObject()
        merged.merge(defaultCfg)
        merged.merge(resolvedCfg)
    }

}
