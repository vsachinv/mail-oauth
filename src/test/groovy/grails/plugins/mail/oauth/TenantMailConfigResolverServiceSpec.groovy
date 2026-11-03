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

    void "tenant props never leak into the global config or into other tenants"() {
        given: 'a global props map and two tenants that each add a different prop'
        config.merge([
                grails   : [mail: [host: 'smtp.example.com', port: 587, props: ['mail.smtp.auth': 'true']]],
                tenant_42: [grails: [mail: [username: 't42', props: ['mail.smtp.starttls.enable': 'true']]]],
                tenant_43: [grails: [mail: [username: 't43', props: ['mail.smtp.ssl.enable': 'true']]]],
                tenant_44: [grails: [mail: [username: 't44']]],
        ])
        def globalProps = config.grails.mail.props

        when:
        ConfigObject t42 = service.resolve(42L)
        ConfigObject t43 = service.resolve(43L)
        ConfigObject t44 = service.resolve(44L)

        then: 'each tenant sees the global defaults plus only its own additions'
        // Grails config also expands dotted keys into nested maps, so assert on the flat keys and on
        // the nested path rather than on map equality.
        t42.props['mail.smtp.auth'] == 'true'
        t42.props['mail.smtp.starttls.enable'] == 'true'
        !t42.props.containsKey('mail.smtp.ssl.enable')
        !t42.props.mail.smtp.containsKey('ssl')
        t43.props['mail.smtp.ssl.enable'] == 'true'
        !t43.props.containsKey('mail.smtp.starttls.enable')
        !t43.props.mail.smtp.containsKey('starttls')
        t44.props['mail.smtp.auth'] == 'true'
        !t44.props.containsKey('mail.smtp.starttls.enable')
        !t44.props.containsKey('mail.smtp.ssl.enable')
        !t44.props.mail.smtp.containsKey('starttls')
        !t44.props.mail.smtp.containsKey('ssl')
        t42.host == 'smtp.example.com'
        t44.port == 587

        and: 'the global map is untouched and not shared with any result'
        !globalProps.containsKey('mail.smtp.starttls.enable')
        !globalProps.containsKey('mail.smtp.ssl.enable')
        !globalProps.mail.smtp.containsKey('starttls')
        !globalProps.mail.smtp.containsKey('ssl')
        !t42.props.is(globalProps)
        !t44.props.is(globalProps)
        !t44.props.mail.is(globalProps.mail)

        and: 'the tenant blocks in application config are untouched too'
        !config.tenant_42.grails.mail.props.containsKey('mail.smtp.auth')
        !config.tenant_42.grails.mail.containsKey('host')
        !config.tenant_44.grails.mail.containsKey('props')
    }

    void "a tenant can override a global prop without changing it for others"() {
        given:
        config.merge([
                grails   : [mail: [props: ['mail.smtp.starttls.enable': 'true']]],
                tenant_50: [grails: [mail: [username: 't50', props: ['mail.smtp.starttls.enable': 'false']]]],
                tenant_51: [grails: [mail: [username: 't51']]],
        ])

        expect:
        service.resolve(50L).props['mail.smtp.starttls.enable'] == 'false'
        service.resolve(51L).props['mail.smtp.starttls.enable'] == 'true'
        config.grails.mail.props['mail.smtp.starttls.enable'] == 'true'
    }
}
