# Certificate-Based Authentication Design

## Overview

This document describes the design changes made to support certificate-based authentication (client assertion via JWT) as an alternative to client secret for Microsoft Azure AD token acquisition in the `mail-oauth` plugin.

Both the **sender** (`MailOAuthService`) and the **reader** (`InMemoryReaderTokenStoreService`) paths are covered. The OAuth grant type is unchanged — certificate auth replaces only the **app credential** (secret → signed JWT assertion).

---

## Motivation

Azure AD supports two forms of app credentials for client-credentials and auth-code flows:
- **Client secret** — a shared string registered in Azure AD
- **Client certificate** — a private key used to sign a JWT assertion (`client_assertion`), which Azure AD verifies against the registered public certificate

Certificate-based auth is preferred in production environments because:
- Private keys never leave the server (no shared secret transmitted)
- Certificates can be rotated via keystore management without changing Azure AD config
- Aligns with Azure Managed Identity and enterprise security policies

---

## Strategy Pattern

A `GraphAuthStrategy` interface abstracts how a token is acquired, allowing the two credential types to be swapped transparently.

### Interface — `grails.plugins.mail.graph.auth.GraphAuthStrategy`

```groovy
interface GraphAuthStrategy {
    OAuth2AccessToken acquireToken(OAuth20Service service)   // client-credentials grant
    OAuth2AccessToken refreshToken(OAuth20Service service, String refreshToken)  // refresh-token grant
    boolean isDaemon()  // true = no interactive flow supported
}
```

### Implementations

| Class | Credential | `isDaemon()` | Backed by |
|---|---|---|---|
| `SecretKeyStrategy` | Client secret | configurable | ScribeJava `OAuth20Service` |
| `CertificateStrategy` | PFX certificate | always `true` | JDK-signed JWT + OkHttp direct POST |

### Factory — `GraphAuthStrategyFactory`

Two static `build()` overloads select the right implementation:

```groovy
GraphAuthStrategyFactory.build(Config config)           // used by MailOAuthService (sender)
GraphAuthStrategyFactory.build(GraphConfig graphConfig) // used by InMemoryReaderTokenStoreService (reader)
```

Returns `CertificateStrategy` when `useCertAuth = true`, otherwise `SecretKeyStrategy`.

---

## Certificate Strategy — How It Works

`CertificateStrategy` implements the [OAuth 2.0 client authentication with JWT](https://learn.microsoft.com/en-us/entra/identity-platform/certificate-credentials) spec:

### Step 1 — Load the PFX keystore

```
KeyStore.getInstance('PKCS12')
    .load(FileInputStream(certPath), certPassword)
```

The alias is used to select the key entry. If not provided, the first alias in the keystore is used.

### Step 2 — Build a signed JWT assertion (RS256)

Header:
```json
{ "alg": "RS256", "x5t": "<SHA-1 thumbprint of certificate, base64url>" }
```

Claims:
```json
{
  "aud": "https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token",
  "iss": "{clientId}",
  "sub": "{clientId}",
  "jti": "{random UUID}",
  "nbf": <now>,
  "exp": <now + 600s>
}
```

Signed with the RSA private key from the keystore using the JDK `Signature` API (`SHA256withRSA`); the JWT is assembled manually, so no JOSE library is required.

### Step 3 — POST to the Azure AD token endpoint

```
POST https://login.microsoftonline.com/{tenantId}/oauth2/v2.0/token

grant_type=client_credentials
client_id={clientId}
client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
client_assertion={signed JWT}
scope={scopes}
```

Response is parsed and returned as `OAuth2AccessToken`.

### Refresh / Interactive flow

`refreshToken()` throws `UnsupportedOperationException` — certificate strategy always acquires a fresh token via client-credentials grant (it is always daemon mode).

---

## Files Changed

### New files

| File | Description |
|---|---|
| `src/main/groovy/grails/plugins/mail/graph/auth/GraphAuthStrategy.groovy` | Strategy interface |
| `src/main/groovy/grails/plugins/mail/graph/auth/SecretKeyStrategy.groovy` | Client secret implementation |
| `src/main/groovy/grails/plugins/mail/graph/auth/CertificateStrategy.groovy` | Certificate / JWT assertion implementation |
| `src/main/groovy/grails/plugins/mail/graph/auth/GraphAuthStrategyFactory.groovy` | Factory to select strategy from config or `GraphConfig` |

### Modified files

| File | Change |
|---|---|
| `grails-app/services/grails/plugins/mail/oauth/MailOAuthService.groovy` | Replaced `oAuth20Service` + cert fields with `GraphAuthStrategy`; delegates acquire/refresh to strategy |
| `src/main/groovy/grails/plugins/mail/graph/token/InMemoryReaderTokenStoreService.groovy` | `refreshTokenFor`, `generateAuthCodeURLFor` delegate to strategy; `getTokenFor` treats `useCertAuth` same as `daemon` |
| `src/main/groovy/grails/plugins/mail/graph/GraphConfig.groovy` | Added `useCertAuth`, `certificatePath`, `certificatePassword`, `certificateAlias` |
| `grails-app/conf/application.yml` | Added `useCertAuth`, `certificatePath`, `certificatePassword`, `certificateAlias` config keys |

---

## Configuration

### Sender (`MailOAuthService`)

```yaml
grails:
  mail:
    oAuth:
      enabled: true
      daemon: true               # certificate auth is always daemon mode
      useCertAuth: true
      client_id: <azure-app-client-id>
      tenant_id: <azure-tenant-id>
      api_scope: https://graph.microsoft.com/.default
      certificatePath: /path/to/keystore.pfx
      certificatePassword: <pfx-password>
      certificateAlias: <key-alias>   # optional; first alias used if omitted
      graph:
        enabled: true
```

### Reader (`GraphConfig` — constructed by the consuming app)

```groovy
GraphConfig config = new GraphConfig(
    configName: 'inbox-reader',
    clientId: '<client-id>',
    tenantId: '<tenant-id>',
    scopes: 'https://graph.microsoft.com/.default',
    emailAddress: 'mailbox@company.com',
    daemon: true,
    useCertAuth: true,
    certificatePath: '/path/to/keystore.pfx',
    certificatePassword: '<pfx-password>',
    certificateAlias: '<key-alias>'
)
```

---

## Azure AD Registration Requirements

1. Upload the **public certificate** (`.cer` / `.pem`) to the Azure AD app registration under **Certificates & secrets → Certificates**.
2. The plugin uses the **private key** from the PFX locally to sign the JWT assertion.
3. No client secret is needed or created when using certificate auth.

---

## Dependencies

| Library | Source | Purpose |
|---|---|---|
| JDK `java.security` | Built-in | RS256 JWT signing (no JOSE library needed) |
| `okhttp3` | Transitive via `microsoft-graph` | Token endpoint HTTP POST |
