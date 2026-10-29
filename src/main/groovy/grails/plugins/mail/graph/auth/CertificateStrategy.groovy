package grails.plugins.mail.graph.auth

import com.github.scribejava.core.model.OAuth2AccessToken
import com.github.scribejava.core.oauth.OAuth20Service
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

import java.security.KeyStore
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate

@Slf4j
class CertificateStrategy implements GraphAuthStrategy {

    private final String clientId
    private final String tenantId
    private final String certPath
    private final String certBase64
    private final String certPassword
    private final String alias
    private final String scopes

    private final OkHttpClient httpClient = new OkHttpClient()

    CertificateStrategy(String clientId, String tenantId, String certPath, String certPassword, String alias, String scopes) {
        this(clientId, tenantId, certPath, null, certPassword, alias, scopes)
    }

    /**
     * @param certPath   path to the PFX keystore on disk (used when {@code certBase64} is blank)
     * @param certBase64 the PFX keystore bytes, base64 encoded (standard or MIME/line-wrapped); takes precedence over {@code certPath}
     */
    CertificateStrategy(String clientId, String tenantId, String certPath, String certBase64, String certPassword, String alias, String scopes) {
        this.clientId = clientId
        this.tenantId = tenantId
        this.certPath = certPath
        this.certBase64 = certBase64?.trim() ?: null
        this.certPassword = certPassword
        this.alias = alias
        this.scopes = scopes
    }

    @Override
    OAuth2AccessToken acquireToken(OAuth20Service service) throws Exception {
        log.debug('[GRAPH_AUTH] [CERTIFICATE] Acquiring token via certificate client assertion')
        String assertion = buildClientAssertion()
        return postTokenRequest(assertion)
    }

    @Override
    OAuth2AccessToken refreshToken(OAuth20Service service, String refreshToken) throws Exception {
        throw new UnsupportedOperationException(
            'Refresh token grant is not supported with certificate authentication. Certificate strategy always uses client credentials grant.')
    }

    @Override
    boolean isDaemon() {
        return true
    }

    private String buildClientAssertion() {
        if (!scopes) {
            throw new IllegalStateException('Certificate auth is enabled but no scope/api_scope is configured')
        }
        KeyStore keyStore = KeyStore.getInstance('PKCS12')
        openKeystore().withCloseable { stream ->
            keyStore.load(stream, certPassword?.toCharArray() ?: new char[0])
        }

        String keyAlias = alias ?: keyStore.aliases().nextElement()
        PrivateKey privateKey = keyStore.getKey(keyAlias, certPassword?.toCharArray() ?: new char[0]) as PrivateKey
        X509Certificate certificate = keyStore.getCertificate(keyAlias) as X509Certificate
        if (!privateKey || !certificate) {
            throw new IllegalStateException("No private key/certificate found in ${certificateSource()} for alias '${keyAlias}'")
        }

        // RS256 JWT built with the JDK only (no JOSE library): base64url(header).base64url(claims).base64url(signature)
        String x5t = base64Url(MessageDigest.getInstance('SHA-1').digest(certificate.encoded))
        long now = System.currentTimeMillis().intdiv(1000L)
        Map header = [alg: 'RS256', typ: 'JWT', x5t: x5t]
        Map claims = [
                aud: "https://login.microsoftonline.com/${tenantId}/oauth2/v2.0/token".toString(),
                iss: clientId,
                sub: clientId,
                jti: UUID.randomUUID().toString(),
                nbf: now,
                exp: now + 600
        ]
        String signingInput = base64Url(JsonOutput.toJson(header).getBytes(StandardCharsets.UTF_8)) + '.' +
                base64Url(JsonOutput.toJson(claims).getBytes(StandardCharsets.UTF_8))

        Signature signer = Signature.getInstance('SHA256withRSA')
        signer.initSign(privateKey)
        signer.update(signingInput.getBytes(StandardCharsets.US_ASCII))
        return signingInput + '.' + base64Url(signer.sign())
    }

    /**
     * Keystore bytes from {@code certificateBase64} when set, otherwise from the file at {@code certificatePath}.
     * Re-read on every call so a rotated value/file is picked up without a restart. The base64 content is never
     * included in log or exception messages because it is the private key.
     */
    private InputStream openKeystore() {
        if (certBase64) {
            if (certPath) {
                log.debug('[GRAPH_AUTH] [CERTIFICATE] Both certificateBase64 and certificatePath are set; using certificateBase64')
            }
            byte[] bytes
            try {
                // Strip line breaks/whitespace from wrapped values, then decode strictly so garbage is rejected
                bytes = Base64.decoder.decode(certBase64.replaceAll(/\s+/, ''))
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException('Certificate auth is enabled but certificateBase64 is not valid base64', e)
            }
            if (bytes.length == 0) {
                throw new IllegalStateException('Certificate auth is enabled but certificateBase64 decoded to zero bytes')
            }
            return new ByteArrayInputStream(bytes)
        }
        if (!certPath || !new File(certPath).isFile()) {
            throw new IllegalStateException("Certificate auth is enabled but neither certificateBase64 nor a readable certificatePath is configured (certificatePath=${certPath})")
        }
        return new FileInputStream(certPath)
    }

    /** Human-readable name of the keystore source for error messages; never the content. */
    private String certificateSource() {
        certBase64 ? 'certificateBase64' : certPath
    }

    private static String base64Url(byte[] bytes) {
        Base64.urlEncoder.withoutPadding().encodeToString(bytes)
    }

    private OAuth2AccessToken postTokenRequest(String clientAssertion) {
        String tokenUrl = "https://login.microsoftonline.com/${tenantId}/oauth2/v2.0/token"

        FormBody body = new FormBody.Builder()
                .add('grant_type', 'client_credentials')
                .add('client_id', clientId)
                .add('client_assertion_type', 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer')
                .add('client_assertion', clientAssertion)
                .add('scope', scopes)
                .build()

        Request request = new Request.Builder().url(tokenUrl).post(body).build()

        String responseBody
        int code
        boolean successful
        httpClient.newCall(request).execute().withCloseable { Response response ->
            responseBody = response.body().string()
            code = response.code()
            successful = response.successful
        }

        if (!successful) {
            log.error('[GRAPH_AUTH] [CERTIFICATE] Token request failed [HTTP {}]: {}', code, responseBody)
            throw new Exception("Certificate token request failed [HTTP ${code}]: ${responseBody}")
        }

        Map json = new JsonSlurper().parseText(responseBody) as Map
        return new OAuth2AccessToken(
                json.access_token as String,
                json.token_type as String,
                json.expires_in as Integer,
                null,
                json.scope as String,
                responseBody
        )
    }
}

