package grails.plugins.mail.oauth

import spock.lang.Specification
import spock.lang.Unroll

class MailOAuthUtilSpec extends Specification {

    @Unroll
    void "parseState returns an empty map for #scenario input"() {
        expect:
        MailOAuthUtil.parseState(input) == [:]

        where:
        scenario | input
        'null'   | null
        'blank'  | ''
    }

    void "buildOAuthState and parseState round-trip the tenantId and state"() {
        given:
        String encoded = MailOAuthUtil.buildOAuthState(42L, 'abc123')

        when:
        Map<String, String> parsed = MailOAuthUtil.parseState(encoded)

        then:
        parsed.tenantId == '42'
        parsed.state == 'abc123'
    }

    void "parseState skips malformed segments that carry no key=value pair"() {
        expect:
        MailOAuthUtil.parseState('tenantId=7|garbage|state="x"') == [tenantId: '7', state: 'x']
    }
}
