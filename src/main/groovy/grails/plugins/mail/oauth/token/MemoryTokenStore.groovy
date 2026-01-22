package grails.plugins.mail.oauth.token

import groovy.util.logging.Slf4j

import java.util.concurrent.ConcurrentHashMap

@Slf4j
class MemoryTokenStore implements TokenStore {

    /**
     * tenantId -> OAuthToken
     */
    private final Map<Long, OAuthToken> tokenStoreMap =
            new ConcurrentHashMap<>()

    @Override
    void saveToken(Long tenantId, OAuthToken token) {

        if (!tenantId) {
            throw new IllegalArgumentException(
                    "tenantId must not be null for OAuth token"
            )
        }

        if (!token) {
            log.warn("Attempt to save null OAuthToken for tenant [{}]", tenantId)
            return
        }

        tokenStoreMap.put(tenantId, token)

        log.debug(
                "OAuth token stored in memory for tenant [{}], expires at [{}]",
                tenantId,
                token.expireAt
        )
    }

    @Override
    OAuthToken getToken(Long tenantId) {

        if (!tenantId) {
            throw new IllegalArgumentException(
                    "tenantId must not be null when retrieving OAuth token"
            )
        }

        OAuthToken token = tokenStoreMap.get(tenantId)

        if (!token) {
            log.debug(
                    "No OAuth token found in memory for tenant [{}]",
                    tenantId
            )
        }

        return token
    }

    @Override
    void revokeToken(Long tenantId) {

        if (!tenantId) {
            throw new IllegalArgumentException(
                    "tenantId must not be null when revoking OAuth token"
            )
        }

        tokenStoreMap.remove(tenantId)

        log.info(
                "OAuth token revoked from memory for tenant [{}]",
                tenantId
        )
    }

    /**
     * Optional helper for refresh job / monitoring
     */
    Set<Long> getTenantsWithTokens() {
        return tokenStoreMap.keySet()
    }


}
