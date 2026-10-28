# Tenant Configuration Reference — Graph mail per use case

Which keys are **mandatory** for each way a tenant can send mail through Microsoft Graph on the
multi-tenant (Grails 7) line of `mail-oauth`. Everything here was checked against the code; where a
key is read from the *global* block only, it says so, because that is the most common mistake.

Companion documents: [`PLUGIN_DESIGN.md`](PLUGIN_DESIGN.md) (how the flows work),
[`MULTI_TENANCY.md`](MULTI_TENANCY.md) (config resolution rules),
[`../SETUP_GUIDE.md`](../SETUP_GUIDE.md) (Azure side: app registration, permissions, consent, PFX).

> **Two "tenant" ids.** `tenant_<id>` is your *application* tenant id (a `Long`).
> `oAuth.tenant_id` is the *Microsoft Entra directory* id of that tenant's app registration.

---

## 1. Rules that apply to every use case

1. **Nothing under `oAuth.*` is inherited.** A tenant block gets only `host`, `port` and `props`
   from the global `grails.mail`. Every other key, including `daemon`, `disabled` and all
   credentials, must be written in the tenant's own block.
2. **A tenant without a `tenant_<id>.grails.mail` block cannot send.** Resolution throws
   `IllegalStateException`; there is no fallback to the global configuration.
3. **`daemon` unset means `false`.** There is no fallback to `grails.mail.oAuth.daemon`.
4. **Some keys are global only.** They are read through static helpers from `grails.mail.oAuth`
   and are ignored if placed in a tenant block (see section 2).
5. **Secrets** (`secret_val`, `certificatePassword`, `certificateBase64`) must come from a vault or
   environment variable, never from a tracked file.

---

## 2. Global keys — set once, not per tenant

| Key | Mandatory | Notes |
|---|---|---|
| `DEFAULT_TENANT_ID` | yes | Without it no tenant id maps to the org-level `grails.mail` block |
| `grails.mail.oAuth.api_scope` | yes | Must be `https://graph.microsoft.com/.default` for Graph. The plugin default is the SMTP scope. Used by the ScribeJava service and the Graph client for every tenant |
| `grails.mail.oAuth.callback_url` | yes | Redirect URI for consent and interactive login. Must be registered on **every** tenant's Entra app registration |
| `grails.mail.oAuth.redirect.uri` | no | Where `/mailOAuth/*` lands afterwards. Default `/mailOAuth/index` |
| `grails.mail.oAuth.graph.attachmentMax` | no | Attachment size limit in MB, default 3 |
| `grails.mail.oAuth.graph.http.*Timeout` | no | Graph client timeouts |
| Spring bean `tenantContextProvider` | yes | Supplied by the host application. The context does not start without it |

```groovy
DEFAULT_TENANT_ID = 1L
grails.mail.oAuth.api_scope    = 'https://graph.microsoft.com/.default'
grails.mail.oAuth.callback_url = 'https://app.company.com/mailOAuth/callback'
grails.mail.oAuth.redirect.uri = '/mailOAuth/index'
```

---

## 3. Keys common to all three use cases — per tenant

Under `tenant_<id>.grails.mail` (or plain `grails.mail` for the default tenant):

| Key | Mandatory | Notes |
|---|---|---|
| `oAuth.enabled` | yes, `true` | Selects an OAuth sender instead of plain SMTP |
| `oAuth.graph.enabled` | yes, `true` | Selects Graph over OAuth SMTP |
| `oAuth.client_id` | yes | Entra application (client) id |
| `oAuth.tenant_id` | yes | Entra directory id. Do not rely on the global default `common`, especially for daemon flows |
| `username` | yes | The mailbox. Daemon flows send as `/users/{username}`; the interactive flow checks that the signed-in user equals it |
| `disabled` | no | `true` switches this tenant's sending off: `sendMail` logs and returns `null` |

---

## 4. Use case A — Certificate, daemon app (app-only)

Token: `client_credentials` with a JWT client assertion signed by the PFX private key.
Graph call: `POST /users/{username}/sendMail`. No user ever signs in.

| Key | Mandatory | Notes |
|---|---|---|
| `oAuth.daemon` | yes, `true` | Certificate auth is app-only; with `daemon` unset/false the context build fails with `IllegalStateException` |
| `oAuth.useCertAuth` | yes, `true` | Selects `CertificateStrategy` |
| `oAuth.certificatePath` **or** `oAuth.certificateBase64` | yes, one of them | File path to the PFX, or its bytes base64 encoded. Base64 wins if both are set |
| `oAuth.api_scope` | yes | **Also required in the tenant block.** `CertificateStrategy` reads the scope from the tenant config and throws without it. This is the one key that is both global and per tenant |
| `oAuth.certificatePassword` | only if the PFX has one | |
| `oAuth.certificateAlias` | no | First alias in the keystore is used if omitted |
| `oAuth.secret_val` | no | Not used in this mode |

Entra prerequisites: certificate public key uploaded to the app registration, `Mail.Send` as an
**Application** permission with **admin consent** granted (via `/mailOAuth/generate` for this tenant,
or in the portal / CLI). With consent already present, the first send acquires the token lazily.

```groovy
tenant_42.grails.mail.username                   = 'noreply@tenant42.com'
tenant_42.grails.mail.oAuth.enabled              = true
tenant_42.grails.mail.oAuth.graph.enabled        = true
tenant_42.grails.mail.oAuth.daemon               = true
tenant_42.grails.mail.oAuth.useCertAuth          = true
tenant_42.grails.mail.oAuth.client_id            = '<app id 42>'
tenant_42.grails.mail.oAuth.tenant_id            = '<entra directory id 42>'
tenant_42.grails.mail.oAuth.api_scope            = 'https://graph.microsoft.com/.default'
tenant_42.grails.mail.oAuth.certificatePath      = '/etc/app/tenant42.pfx'
// or: tenant_42.grails.mail.oAuth.certificateBase64 = System.getenv('TENANT42_PFX_B64')
tenant_42.grails.mail.oAuth.certificatePassword  = System.getenv('TENANT42_PFX_PASSWORD')
tenant_42.grails.mail.oAuth.certificateAlias     = 'mailcert'   // optional
```

---

## 5. Use case B — Client secret, daemon app (app-only)

Token: `client_credentials` with the client secret via ScribeJava.
Graph call: `POST /users/{username}/sendMail`. No user ever signs in.

| Key | Mandatory | Notes |
|---|---|---|
| `oAuth.daemon` | yes, `true` | In the tenant block |
| `oAuth.secret_val` | yes | Client secret, externalised |
| `oAuth.useCertAuth` | no | Leave unset or `false` |
| `oAuth.api_scope` (tenant level) | no | Only the certificate strategy reads the tenant-level scope; this mode uses the global one |

Entra prerequisites: client secret created, `Mail.Send` as an **Application** permission with
**admin consent** granted (same options as use case A).

```groovy
tenant_43.grails.mail.username                   = 'noreply@tenant43.com'
tenant_43.grails.mail.oAuth.enabled              = true
tenant_43.grails.mail.oAuth.graph.enabled        = true
tenant_43.grails.mail.oAuth.daemon               = true
tenant_43.grails.mail.oAuth.client_id            = '<app id 43>'
tenant_43.grails.mail.oAuth.tenant_id            = '<entra directory id 43>'
tenant_43.grails.mail.oAuth.secret_val           = System.getenv('TENANT43_CLIENT_SECRET')
```

---

## 6. Use case C — Personal token, interactive (delegated)

Token: authorization-code flow. A user signs in once through `/mailOAuth/generate`; the plugin stores
the token and refreshes it with the refresh token from then on.
Graph call: `POST /me/sendMail` as that user.

| Key | Mandatory | Notes |
|---|---|---|
| `oAuth.daemon` | unset or `false` | |
| `oAuth.secret_val` | yes | Client secret used for the code exchange and refresh |
| `username` | yes | Must equal the `mail` or `userPrincipalName` of the account that signs in, or the callback rejects the token with "Generated token is not for account …" |
| `oAuth.useCertAuth` | no | Must not be `true`: certificate auth requires daemon |

Entra prerequisites: `Mail.Send` as a **Delegated** permission plus `offline_access` (so a refresh
token is issued), and the global `callback_url` registered as a Web redirect URI. The user must
complete `/mailOAuth/generate` once per tenant to seed the token; it is in-memory, so repeat after a
restart.

```groovy
tenant_44.grails.mail.username                   = 'alice@tenant44.com'
tenant_44.grails.mail.oAuth.enabled              = true
tenant_44.grails.mail.oAuth.graph.enabled        = true
tenant_44.grails.mail.oAuth.daemon               = false
tenant_44.grails.mail.oAuth.client_id            = '<app id 44>'
tenant_44.grails.mail.oAuth.tenant_id            = '<entra directory id 44>'
tenant_44.grails.mail.oAuth.secret_val           = System.getenv('TENANT44_CLIENT_SECRET')
```

---

## 7. Use case D — OAuth disabled, plain SMTP (falls back to the global mailSender)

A tenant block with `oAuth.enabled` unset or `false` is routed to the standard Grails Mail builder.
**That builder sends through the single application-wide `mailSender` bean**, which Grails Mail
creates at startup from the *global* `grails.mail` SMTP settings. The tenant's own SMTP connection
keys are bound but not used for the connection.

| Key | Mandatory | Notes |
|---|---|---|
| `tenant_<id>.grails.mail` block | yes | Must exist, or resolution throws. Any key is enough, for example `username` |
| `oAuth.enabled` | unset or `false` | |
| global `grails.mail.host` / `port` / `username` / `password` / `props` | yes | **These are what the mail actually goes through.** They configure the global `JavaMailSender` |
| tenant `host` / `port` / `username` / `password` / `props` | ignored for the connection | Bound into `MailConfigurationProperties` but the global sender bean is used regardless |
| tenant `default.from`, `overrideAddress` | honoured | Read by the builder from the tenant's bound properties |
| tenant `disabled` | honoured | `sendMail` returns `null` |
| tenant `poolSize` | honoured | Size of this tenant's dispatch executor |

What this means in practice:

- All non-OAuth tenants share **one SMTP relay and one SMTP account**: the global one.
- There is **no warning** when a tenant sets a different `host` or `username`; the message leaves
  silently through the global relay. If the global block has no SMTP settings, the send fails at
  dispatch with a connection or authentication error from the global sender.
- The tenant id is still propagated to the worker thread and the executor is still per tenant, so
  isolation of *dispatch* holds; isolation of *transport* does not.
- Per-tenant plain-SMTP credentials are therefore **not supported today**. Use an OAuth mode
  (A, B or C) when a tenant needs its own mailbox or relay.

```groovy
// global: the relay every non-OAuth tenant uses
grails.mail.host     = 'smtp.company.com'
grails.mail.port     = 587
grails.mail.username = 'relay@company.com'
grails.mail.password = System.getenv('SMTP_RELAY_PASSWORD')
grails.mail.props    = ['mail.smtp.auth': 'true', 'mail.smtp.starttls.enable': 'true']

// tenant 45: plain SMTP via the global sender; only from/override/disabled/poolSize are per tenant
tenant_45.grails.mail.default.from   = 'noreply@tenant45.com'
tenant_45.grails.mail.oAuth.enabled  = false
```

---

## 8. Side-by-side

| Key | A: certificate daemon | B: secret daemon | C: personal token | D: OAuth disabled |
|---|---|---|---|---|
| `oAuth.enabled` | `true` | `true` | `true` | unset / `false` |
| `oAuth.graph.enabled` | `true` | `true` | `true` | — |
| `oAuth.client_id` | required | required | required | — |
| `oAuth.tenant_id` | required | required | required | — |
| `username` | required (send-as mailbox) | required (send-as mailbox) | required (must match signed-in user) | ignored (global SMTP account used) |
| `oAuth.daemon` | `true` | `true` | unset / `false` | — |
| `oAuth.useCertAuth` | `true` | — | — | — |
| `oAuth.certificatePath` / `certificateBase64` | one required | — | — | — |
| `oAuth.certificatePassword` | if PFX has one | — | — | — |
| `oAuth.certificateAlias` | optional | — | — | — |
| `oAuth.secret_val` | — | required | required | — |
| `oAuth.api_scope` in tenant block | **required** | — | — | — |
| Transport | Graph, tenant's app | Graph, tenant's app | Graph, user's token | **global `mailSender` bean (global SMTP settings)** |
| Entra permission | `Mail.Send` Application + admin consent | `Mail.Send` Application + admin consent | `Mail.Send` Delegated + `offline_access` | none |
| First token | lazy on first send, or `/mailOAuth/generate` | lazy on first send, or `/mailOAuth/generate` | user must run `/mailOAuth/generate` | n/a |
| Renewal | re-sign JWT, re-acquire | re-acquire with secret | refresh token | n/a |

---

## 9. What fails, and where, when a key is missing

| Missing or wrong | Symptom |
|---|---|
| `tenant_<id>.grails.mail` block absent | `IllegalStateException: Mail configuration is not found for tenant` at send time |
| `useCertAuth=true` with `daemon` unset | `IllegalStateException: useCertAuth is true but daemon is false` when the context is built |
| Certificate tenant without `oAuth.api_scope` in its block | `IllegalStateException: no scope/api_scope is configured` on the first token request |
| Neither `certificatePath` nor `certificateBase64` | `IllegalStateException: neither certificateBase64 nor a readable certificatePath is configured` |
| `secret_val` missing in use case B or C | ScribeJava token request fails (`AADSTS7000215` or similar) |
| Global `api_scope` left at the SMTP default | Graph returns 401/403 on send |
| `username` not matching the signed-in user (C) | Callback error "Generated token is not for account …" |
| `daemon=true` without admin consent | `AADSTS65001` / insufficient privileges on the token request |
| `disabled=true` on the tenant | `sendMail` returns `null`; `/mailOAuth/sendTestMail` shows "Mail sending is disabled by configuration for this tenant" |
| `oAuth.enabled=false` with tenant-specific `host`/`username` (D) | No error. Mail leaves through the **global** SMTP relay and account; the tenant's SMTP keys are ignored |
| `oAuth.enabled=false` and no global SMTP settings (D) | Connection/authentication error from the global `JavaMailSender` at dispatch |
