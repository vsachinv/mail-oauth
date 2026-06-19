package grails.plugins.mail.graph

import groovy.transform.CompileStatic
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Injects the Microsoft Graph "Prefer: IdType=\"ImmutableId\"" header on every outbound
 * request so the SDK receives/returns IDs that do not embed a server-side changeKey.
 *
 * Without this, Message.id rotates on every mutation (flag flip, move, patch) and
 * subsequent calls referencing the cached id fail with 404 ResourceNotFound.
 *
 * Uses addHeader so any caller-set Prefer directive is preserved (RFC 7240 permits
 * multiple Prefer headers; receivers MUST combine them).
 *
 */
@CompileStatic
class ImmutableIdHeaderInterceptor implements Interceptor {
    static final String PREFER_HEADER_NAME = "Prefer"
    static final String PREFER_IMMUTABLE_ID_VALUE = 'IdType="ImmutableId"'

    @Override
    Response intercept(Chain chain) throws IOException {
        Request original = chain.request()
        Request modified = original.newBuilder()
            .addHeader(PREFER_HEADER_NAME, PREFER_IMMUTABLE_ID_VALUE)
            .build()
        return chain.proceed(modified)
    }
}
