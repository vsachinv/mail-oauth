package grails.plugins.mail.graph

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import spock.lang.Specification

/**
 * The interceptor is registered unconditionally inside GraphApiClient.getClientFor — there is
 * no opt-out flag — so these tests focus on the interceptor's behavioural contract:
 *   1. Adds "Prefer: IdType=\"ImmutableId\"" when the request has no Prefer header.
 *   2. Preserves any caller-set Prefer directive (uses addHeader, not header replacement —
 *      RFC 7240 permits multiple Prefer headers, receivers MUST combine them).
 *   3. Forwards the modified request via chain.proceed exactly once.
 *   4. Returns whatever chain.proceed returns (transparent pipeline element).
 */
class ImmutableIdHeaderInterceptorSpec extends Specification {

    ImmutableIdHeaderInterceptor interceptor = new ImmutableIdHeaderInterceptor()

    def "adds Prefer: IdType=\"ImmutableId\" to a request that has no Prefer header"() {
        given:
        Request original = new Request.Builder().url("https://graph.microsoft.com/v1.0/me/messages").build()
        Interceptor.Chain chain = Mock()
        chain.request() >> original

        when:
        interceptor.intercept(chain)

        then:
        1 * chain.proceed({ Request r ->
            List<String> preferValues = r.headers('Prefer')
            preferValues.size() == 1 && preferValues[0] == 'IdType="ImmutableId"'
        }) >> stubOkResponse(original)
    }

    def "preserves caller-set Prefer header alongside IdType=\"ImmutableId\" (RFC 7240 multi-header)"() {
        given:
        Request original = new Request.Builder().url("https://graph.microsoft.com/v1.0/me/messages")
                .addHeader('Prefer', 'outlook.body-content-type="text"')
                .build()
        Interceptor.Chain chain = Mock()
        chain.request() >> original

        when:
        interceptor.intercept(chain)

        then:
        1 * chain.proceed({ Request r ->
            List<String> preferValues = r.headers('Prefer')
            preferValues.size() == 2 &&
                    preferValues.any { it == 'outlook.body-content-type="text"' } &&
                    preferValues.any { it == 'IdType="ImmutableId"' }
        }) >> stubOkResponse(original)
    }

    def "calls chain.proceed exactly once with the modified request"() {
        given:
        Request original = new Request.Builder().url("https://graph.microsoft.com/v1.0/me/mailFolders").build()
        Interceptor.Chain chain = Mock()
        chain.request() >> original

        when:
        interceptor.intercept(chain)

        then:
        1 * chain.proceed(_) >> stubOkResponse(original)
    }

    def "returns the response produced by chain.proceed unchanged"() {
        given:
        Request original = new Request.Builder().url("https://graph.microsoft.com/v1.0/me").build()
        Response stub = stubOkResponse(original)
        Interceptor.Chain chain = Mock()
        chain.request() >> original
        chain.proceed(_) >> stub

        expect:
        interceptor.intercept(chain).is(stub)
    }

    def "does not modify the original request (immutability — addHeader returns a new builder)"() {
        given:
        Request original = new Request.Builder().url("https://graph.microsoft.com/v1.0/me/messages").build()
        Interceptor.Chain chain = Mock()
        chain.request() >> original

        when:
        interceptor.intercept(chain)

        then:
        // OkHttp Request is immutable; the original instance must still have no Prefer header.
        original.headers('Prefer').isEmpty()
        1 * chain.proceed(_) >> stubOkResponse(original)
    }

    def "header name and value constants match Microsoft Graph contract"() {
        expect:
        ImmutableIdHeaderInterceptor.PREFER_HEADER_NAME == 'Prefer'
        ImmutableIdHeaderInterceptor.PREFER_IMMUTABLE_ID_VALUE == 'IdType="ImmutableId"'
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private static Response stubOkResponse(Request request) {
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(ResponseBody.create(MediaType.parse("application/json"), '{}'))
                .build()
    }
}
