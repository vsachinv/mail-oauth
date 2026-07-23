package grails.plugins.mail.oauth.token

import spock.lang.Specification

class MemoryTokenStoreSpec extends Specification {

    MemoryTokenStore store = new MemoryTokenStore()

    void "saveToken rejects a null tenantId"() {
        when:
        store.saveToken(null, new OAuthToken(accessToken: 'a'))

        then:
        thrown(IllegalArgumentException)
    }

    void "getToken rejects a null tenantId"() {
        when:
        store.getToken(null)

        then:
        thrown(IllegalArgumentException)
    }

    void "revokeToken rejects a null tenantId"() {
        when:
        store.revokeToken(null)

        then:
        thrown(IllegalArgumentException)
    }

    void "saving a null token is a no-op and stores nothing"() {
        when:
        store.saveToken(1L, null)

        then:
        store.getToken(1L) == null
        !store.tenantsWithTokens.contains(1L)
    }

    void "a saved token is retrievable and isolated per tenant"() {
        given:
        OAuthToken t1 = new OAuthToken(accessToken: 't1')
        OAuthToken t2 = new OAuthToken(accessToken: 't2')

        when:
        store.saveToken(1L, t1)
        store.saveToken(2L, t2)

        then:
        store.getToken(1L).is(t1)
        store.getToken(2L).is(t2)
        store.tenantsWithTokens == ([1L, 2L] as Set)
    }

    void "revoking removes only the target tenant's token"() {
        given:
        store.saveToken(1L, new OAuthToken(accessToken: 't1'))
        store.saveToken(2L, new OAuthToken(accessToken: 't2'))

        when:
        store.revokeToken(1L)

        then:
        store.getToken(1L) == null
        store.getToken(2L) != null
    }

    void "getToken returns null for an unknown tenant"() {
        expect:
        store.getToken(999L) == null
    }
}
