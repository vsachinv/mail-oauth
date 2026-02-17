package grails.plugins.mail.oauth

import grails.gorm.services.Service
import grails.plugins.mail.graph.GraphApiClient
import grails.plugins.mail.graph.token.TokenBasedAuthCredential
import groovy.util.logging.Slf4j

import java.util.concurrent.ConcurrentHashMap

@Slf4j
@Service
class TenantGraphClientRegistryService {
    MailOAuthService mailOAuthService

    private final Map<Long, CachedGraphClient> graphClientCache = new ConcurrentHashMap<>()

    private static class CachedGraphClient {
        String signature
        GraphApiClient client
    }

    GraphApiClient getClient(Long tenantId, ConfigObject cfg) {
        String signature = graphConfigSignature(cfg)
        CachedGraphClient cached = graphClientCache.get(tenantId)
        if (cached && cached.signature == signature) {
            log.info("Client found in cache")
            return cached.client
        }
        synchronized (("GRAPH_CLIENT_" + tenantId).intern()) {

            cached = graphClientCache.get(tenantId)
            if (cached && cached.signature == signature) {
                return cached.client
            }
            GraphApiClient newClient = buildClient()
            graphClientCache.put(tenantId, new CachedGraphClient(
                    signature: signature,
                    client: newClient
            ))

            log.info("[GRAPH MAIL] GraphApiClient cache refreshed for tenantId={}", tenantId)
            return newClient
        }
    }
    void evict(Long tenantId) {
        graphClientCache.remove(tenantId)
        log.info("[GRAPH MAIL] GraphApiClient evicted for tenantId={}", tenantId)
    }

    void evictAll() {
        graphClientCache.clear()
        log.info("[GRAPH MAIL] GraphApiClient cache cleared for all tenants")
    }

    private GraphApiClient buildClient() {
        return new GraphApiClient(
                new TokenBasedAuthCredential(mailOAuthService: mailOAuthService),
                MailOAuthUtil.apiScope(),
                MailOAuthUtil.isDebug(),
                MailOAuthUtil.connectionTimeOut(),
                MailOAuthUtil.writeTimeOut(),
                MailOAuthUtil.readTimeOut()
        )
    }

    private static String graphConfigSignature(ConfigObject cfg) {
        return [
                cfg?.oAuth?.enabled,
                cfg?.oAuth?.graph?.enabled,
                cfg?.oAuth?.client_id,
                cfg?.oAuth?.tenant_id
        ].collect { it?.toString() ?: "" }.join("|")
    }
}
