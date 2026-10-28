package grails.plugins.mail.graph

import groovy.transform.CompileStatic

@CompileStatic
class GraphConfig implements Serializable {

    private static final long serialVersionUID = 1962358793540268673L

    String configName //Should be unique and mandatory
    String tenantId
    String clientId
    String secretId
    String scopes
    String callbackUrl
    //IF daemon then emailAddress is mandatory or using shared account then also can use.
    String emailAddress = null
    boolean isShared = false
    boolean daemon = false
    boolean debug = false
    boolean useCertAuth = false
    String certificatePath        // path to PFX (PKCS12) keystore holding the private key + certificate
    String certificateBase64      // PFX bytes base64 encoded; takes precedence over certificatePath. Private key: never log it
    String certificatePassword    // PFX password (optional if the keystore has none)
    String certificateAlias       // key alias inside the PFX; first alias is used if omitted
}
