package grails.plugins.mail.graph.auth

import grails.plugins.mail.graph.GraphConfig
import groovy.json.JsonSlurper
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir

import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.X509Certificate

class CertificateStrategySpec extends Specification {

    @TempDir
    @Shared
    File tempDir

    @Shared File pfx
    @Shared String password = 'changeit'
    @Shared String alias = 'mailcert'

    def setupSpec() {
        pfx = new File(tempDir, 'test.pfx')
        def proc = ['keytool', '-genkeypair', '-alias', alias, '-keyalg', 'RSA', '-keysize', '2048',
                    '-storetype', 'PKCS12', '-keystore', pfx.absolutePath, '-storepass', password,
                    '-dname', 'CN=mail-oauth-test', '-validity', '1'].execute()
        proc.waitFor()
        assert proc.exitValue() == 0, "keytool failed: ${proc.err.text}"
    }

    void "factory picks CertificateStrategy only when useCertAuth is set"() {
        expect:
        GraphAuthStrategyFactory.build(new GraphConfig(useCertAuth: true, clientId: 'c', tenantId: 't', scopes: 's',
                certificatePath: pfx.absolutePath, certificatePassword: password)) instanceof CertificateStrategy
        GraphAuthStrategyFactory.build(new GraphConfig(useCertAuth: false, daemon: true)) instanceof SecretKeyStrategy
        GraphAuthStrategyFactory.build(new GraphConfig(useCertAuth: false, daemon: true)).daemon
        !GraphAuthStrategyFactory.build(new GraphConfig(useCertAuth: false, daemon: false)).daemon
    }

    void "certificate strategy is always daemon and does not support refresh"() {
        given:
        def strategy = new CertificateStrategy('c', 't', pfx.absolutePath, password, alias, 's')

        expect:
        strategy.daemon

        when:
        strategy.refreshToken(null, 'rt')

        then:
        thrown(UnsupportedOperationException)
    }

    void "client assertion is a valid RS256 JWT with Azure-required claims and x5t header"() {
        given:
        String clientId = '11111111-2222-3333-4444-555555555555'
        String tenantId = 'my-tenant'
        def strategy = new CertificateStrategy(clientId, tenantId, pfx.absolutePath, password, keyAlias, 'https://graph.microsoft.com/.default')

        when:
        String assertion = strategy.buildClientAssertion()
        String[] parts = assertion.split('\\.')
        def decoder = Base64.urlDecoder
        Map header = new JsonSlurper().parseText(new String(decoder.decode(parts[0]), 'UTF-8')) as Map
        Map claims = new JsonSlurper().parseText(new String(decoder.decode(parts[1]), 'UTF-8')) as Map

        KeyStore ks = KeyStore.getInstance('PKCS12')
        pfx.withInputStream { ks.load(it, password.toCharArray()) }
        X509Certificate cert = ks.getCertificate(alias) as X509Certificate
        String expectedThumbprint = Base64.urlEncoder.withoutPadding().encodeToString(MessageDigest.getInstance('SHA-1').digest(cert.encoded))
        Signature verifier = Signature.getInstance('SHA256withRSA')
        verifier.initVerify(cert.publicKey)
        verifier.update("${parts[0]}.${parts[1]}".getBytes('US-ASCII'))

        then:
        parts.length == 3
        header.alg == 'RS256'
        header.x5t == expectedThumbprint
        verifier.verify(decoder.decode(parts[2]))
        claims.aud == "https://login.microsoftonline.com/${tenantId}/oauth2/v2.0/token".toString()
        claims.iss == clientId
        claims.sub == clientId
        claims.jti
        (claims.exp as long) > System.currentTimeMillis().intdiv(1000L)
        (claims.exp as long) - (claims.nbf as long) == 600

        where:
        keyAlias << ['mailcert', null]   // explicit alias and first-alias fallback
    }

    void "missing certificate file gives a clear error"() {
        when:
        new CertificateStrategy('c', 't', '/does/not/exist.pfx', password, alias, 's').buildClientAssertion()

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('certificatePath')
    }
}
