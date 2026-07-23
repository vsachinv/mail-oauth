package grails.plugins.mail.oauth

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class TenantMailConfigResolverServiceSpec extends Specification implements ServiceUnitTest<TenantMailConfigResolverService> {

    void "resolve rejects a null tenantId"() {
        when:
        service.resolve(null)

        then:
        thrown(IllegalArgumentException)
    }

    void "resolve throws when no mail configuration exists for the tenant"() {
        when:
        service.resolve(987654L)

        then:
        thrown(IllegalStateException)
    }
}
