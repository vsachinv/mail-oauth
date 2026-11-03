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
            // Copy, never alias. ConfigObject.merge merges map-valued entries in place, so handing the
            // live global props map to merge() would let every tenant's props leak into the global
            // config (and from there into every other tenant) for the life of the JVM.
            defaultCfg.props = deepCopy(globalMailConfig.props as Map)
        }

        // Merge: defaults first, then tenant/org config on top (tenant values win).
        // The tenant block is cloned for the same reason: the merge must not write into application config.
        ConfigObject merged = new ConfigObject()
        merged.merge(defaultCfg)
        merged.merge(resolvedCfg.clone() as ConfigObject)
        return merged
    }

    /**
     * Deep copy of a config map. Grails expands dotted keys (mail.smtp.auth) into nested maps as well,
     * so a shallow copy would still alias those nested maps and leak through them.
     */
    private static ConfigObject deepCopy(Map source) {
        ConfigObject copy = new ConfigObject()
        source.each { k, v ->
            copy[k] = (v instanceof Map) ? deepCopy(v as Map) : v
        }
        copy
    }

}
