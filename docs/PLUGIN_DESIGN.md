# mail-oauth Plugin Design

How the `mail-oauth` Grails plugin sends and reads mail through Microsoft OAuth, and how every
step is partitioned by tenant. This document is the end-to-end picture; the companion documents go
deeper on one topic each:

| Document | Covers |
|---|---|
| [`docs/MULTI_TENANCY.md`](MULTI_TENANCY.md) | Tenant context, config merge rules, per-tenant resources in detail |
| [`../SETUP_GUIDE.md`](../SETUP_GUIDE.md) | Azure app registration, certificate creation, consent, troubleshooting |
| [`../design_certificate.md`](../design_certificate.md) | Certificate (client-assertion) auth design and release notes |
| [`../README.md`](../README.md) | Full configuration property reference |

> **Two "tenant" ids.** The *application tenant id* is a `Long` identifying a customer/organization
> in the host application; everything in the plugin is keyed by it. `grails.mail.oAuth.tenant_id`
> is the *Microsoft Entra ID directory* id used when talking to Microsoft. "Tenant" below means the
> application tenant unless stated otherwise.

---

## 1. Big picture

```mermaid
flowchart LR
    subgraph Host["Host application (PV Suite)"]
        APP["Business code<br/>tenantMailService.sendMail { ... }"]
        TCP["tenantContextProvider bean<br/>getCurrentTenantId()"]
        CFG["application config<br/>grails.mail.* (default)<br/>tenant_&lt;id&gt;.grails.mail.* (per tenant)"]
    end

    subgraph Plugin["mail-oauth plugin"]
        TMS["TenantMailService"]
        RES["TenantMailConfigResolverService"]
        OAS["MailOAuthService"]
        STRAT["GraphAuthStrategyFactory<br/>SecretKeyStrategy | CertificateStrategy"]
        TOK["MemoryTokenStore<br/>Map&lt;tenantId, OAuthToken&gt;"]
        GCR["TenantGraphClientRegistryService<br/>Map&lt;tenantId, GraphApiClient&gt;"]
        EXE["TenantMailExecutorRegistry<br/>Map&lt;tenantId, ExecutorService&gt;"]
        CTRL["MailOAuthController<br/>/mailOAuth/*"]
    end

    subgraph MS["Microsoft"]
        ENTRA["Entra ID<br/>login.microsoftonline.com"]
        GRAPH["Graph API<br/>graph.microsoft.com"]
        SMTP["Outlook SMTP<br/>XOAUTH2"]
    end

    APP --> TMS
    TCP --> TMS
    TCP --> CTRL
    CFG --> RES
    TMS --> RES
    TMS --> EXE
    TMS --> GCR
    TMS --> OAS
    CTRL --> OAS
    OAS --> RES
    OAS --> STRAT
    OAS --> TOK
    STRAT --> ENTRA
    GCR --> GRAPH
    TMS --> SMTP
```

Three things to hold on to:

1. **The plugin never decides the tenant.** The host supplies `tenantContextProvider`; the plugin
   carries the id into deeper layers through the `TenantIdContext` ThreadLocal.
2. **Config is resolved per call, per tenant.** Nothing about a tenant's mail setup is cached except
   its token, its Graph client, and its thread pool.
3. **Auth mode is per tenant.** Interactive vs daemon, and secret vs certificate, are decided from the
   tenant's resolved config on every token operation.

---

## 2. Startup: bean wiring

`MailOauthGrailsPlugin.doWithSpring` registers the beans below. Reader beans only exist when
`grails.mail.reader.enabled` is true.

```mermaid
flowchart TD
    P["MailOauthGrailsPlugin.doWithSpring"]
    P --> B1["tokenStore (MemoryTokenStore)"]
    P --> B2["stateStoreService (SessionStateStoreService)"]
    P --> B3["tenantMailConfigResolverService"]
    P --> B4["tenantMailExecutorRegistry"]
    P --> B5["tenantGraphClientRegistryService → mailOAuthService"]
    P --> B6["graphMailMessageBuilderFactory<br/>oauthMailMessageBuilderFactory<br/>mailMessageBuilderFactory (Grails Mail)"]
    P --> B7["tenantMailService<br/>(wires all of the above + tenantContextProvider)"]
    P -. "reader.enabled" .-> R1["readerTokenStoreService"]
    R1 -. "reader.graph.enabled" .-> R2["graphApiClient + graphEmailReaderService"]
    R1 -. "reader.imap.enabled" .-> R3["imapEmailReaderService"]
```

`mailOAuthService` and `MailOAuthController` are Grails artefacts picked up by convention.
`tenantContextProvider` is **not** provided by the plugin; the application context fails to start
without it.

---

## 3. Which tenant, which config

### 3.1 Resolving the tenant id

| Entry point | How the tenant id is obtained |
|---|---|
| `tenantMailService.sendMail { }` | `tenantContextProvider.getCurrentTenantId()` |
| `tenantMailService.sendMailWithTenant(id) { }` | Explicit argument |
| `/mailOAuth/generate`, `refresh`, `revoke`, `tokenStatus` | `tenantContextProvider`, set on `TenantIdContext` for the request, cleared in `finally` |
| `/mailOAuth/callback` | Decoded from the OAuth `state` parameter (`MailOAuthUtil.parseState`) because the callback request has no session tenant |
| Token refresh during a send | `TenantIdContext.getTenantId()` on the worker thread (propagated by `TenantAwareExecutorService`) |

### 3.2 Default tenant vs specific tenant

`TenantMailConfigResolverService.resolve(tenantId)` picks the config tree and merges global
connection defaults underneath it.

```mermaid
flowchart TD
    A["resolve(tenantId)"] --> B{"tenantId ==<br/>config.DEFAULT_TENANT_ID ?"}
    B -- yes --> C["base = grails.mail<br/>(organization / default config)"]
    B -- no --> D["base = tenant_&lt;tenantId&gt;.grails.mail"]
    C --> E{"base empty?"}
    D --> E
    E -- yes --> X["IllegalStateException<br/>'Mail configuration is not found for tenant'"]
    E -- no --> F["defaults = host, port, props<br/>from global grails.mail"]
    F --> G["merged = defaults ⊕ base<br/>(tenant values win)"]
    G --> H["ConfigObject returned to caller"]
```

Consequences:

- The **default tenant** is simply the tenant whose id equals `DEFAULT_TENANT_ID`; it uses the
  classic single-tenant `grails.mail` block unchanged. Existing deployments keep working.
- A **specific tenant** must have its own `tenant_<id>.grails.mail` block. Only `host`, `port` and
  `props` are inherited from the global block; `oAuth.*` is **not** inherited. In particular an
  unset `oAuth.daemon` on a tenant means `false` (see section 5.3).
- Static helpers in `MailOAuthUtil` (`callBackUrl`, `apiScope`, `redirectUri`, timeouts,
  `attachmentMax`) always read the **global** `grails.mail.oAuth` block. These are
  deployment-wide, not per tenant.

---

## 4. Send flow

### 4.1 Sequence

```mermaid
sequenceDiagram
    participant App as Host code
    participant TMS as TenantMailService
    participant RES as ConfigResolver
    participant GCR as GraphClientRegistry
    participant EXE as ExecutorRegistry
    participant W as Tenant worker thread
    participant OAS as MailOAuthService
    participant MS as Microsoft

    App->>TMS: sendMail { to/subject/body }
    TMS->>TMS: tenantId = tenantContextProvider (or explicit)
    TMS->>RES: resolve(tenantId)
    RES-->>TMS: ConfigObject cfg
    TMS->>TMS: resolveBuilder(cfg) → Graph | OAuth SMTP | plain SMTP
    opt Graph send
        TMS->>GCR: getClient(tenantId, cfg)
        GCR-->>TMS: cached or rebuilt GraphApiClient
    end
    TMS->>TMS: TenantIdContext.set(tenantId)
    TMS->>TMS: callable.call(builder)
    TMS->>EXE: executorFor(tenantId, poolSize)
    TMS->>W: builder.sendMessage(executor)
    TMS->>TMS: restore previous TenantIdContext (finally)
    TMS-->>App: MailMessage (dispatched async)

    W->>W: TenantIdContext.set(tenantId) by TenantAwareExecutorService
    W->>OAS: getAccessToken()
    OAS->>OAS: tokenStore.getToken(tenantId)
    alt missing or expired
        OAS->>RES: resolve(tenantId)
        OAS->>OAS: buildContext(cfg) → strategy, daemon
        OAS->>MS: acquire (daemon) or refresh (interactive)
        OAS->>OAS: tokenStore.saveToken(tenantId)
    end
    OAS-->>W: OAuthToken
    W->>MS: Graph sendMail / SMTP XOAUTH2
    W->>W: TenantIdContext.clear()
```

### 4.2 Sender selection

```mermaid
flowchart TD
    S["cfg = resolve(tenantId)"] --> Q1{"cfg.oAuth.enabled ?"}
    Q1 -- no --> P["Plain SMTP<br/>Grails Mail MailMessageBuilderFactory<br/>(username/password)"]
    Q1 -- yes --> Q2{"cfg.oAuth.graph.enabled ?"}
    Q2 -- no --> O["OAuth SMTP<br/>OAuthMailSenderImpl<br/>XOAUTH2 with tenant's access token"]
    Q2 -- yes --> G["Graph API<br/>GraphMailSenderImpl via GraphApiClient<br/>daemon = MailOAuthUtil.isDaemon(cfg)"]
    G --> Q3{"daemon or from ≠ username ?"}
    Q3 -- yes --> U["POST /users/{from}/sendMail<br/>(app-only or shared mailbox)"]
    Q3 -- no --> M["POST /me/sendMail<br/>(delegated user)"]
```

Each tenant can land on a different branch. The default tenant can be on plain SMTP while tenant 42
sends through Graph with a certificate.

---

## 5. Token lifecycle per tenant

### 5.1 Building the tenant OAuth context

Every token operation starts with `MailOAuthService.buildContext(cfg, tenantId)`:

```mermaid
flowchart TD
    C["buildContext(cfg, tenantId)"] --> D["daemon = MailOAuthUtil.isDaemon(cfg)<br/>cfg.oAuth.daemon, unset = false"]
    D --> F["GraphAuthStrategyFactory.build(cfg, daemon)"]
    F --> Q{"cfg.oAuth.useCertAuth ?"}
    Q -- no --> SK["SecretKeyStrategy(daemon)"]
    Q -- yes --> Q2{"daemon ?"}
    Q2 -- no --> ERR["IllegalStateException<br/>certificate auth requires daemon"]
    Q2 -- yes --> CS["CertificateStrategy<br/>(client_id, tenant_id, PFX path/password/alias, api_scope)"]
    SK --> SB["ScribeJava ServiceBuilder(client_id)<br/>apiSecret only if secret_val present<br/>callback = global callback_url<br/>api = MicrosoftAzureActiveDirectory20Api.custom(tenant_id)"]
    CS --> SB
    SB --> CTX["TenantOAuthContext<br/>tenantId, oauthService, daemon, enable, clientId, authStrategy"]
```

### 5.2 Three modes, chosen per tenant

| Mode | Config | Consent step | First token | Renewal | Graph call |
|---|---|---|---|---|---|
| Interactive (delegated) | `daemon=false`, `secret_val` | User signs in via `/mailOAuth/generate` | Auth-code exchange in `/mailOAuth/callback` | `refresh_token` grant | `/me/sendMail` |
| Daemon, client secret | `daemon=true`, `secret_val` | Admin consent (URL from `/mailOAuth/generate`, or portal/CLI) | `client_credentials` grant | Re-acquire with `client_credentials` | `/users/{from}/sendMail` |
| Daemon, certificate | `daemon=true`, `useCertAuth=true`, `certificatePath` or `certificateBase64` | Admin consent (same as above) | `client_credentials` with signed JWT `client_assertion` | Re-sign JWT, re-acquire | `/users/{from}/sendMail` |

### 5.3 How `daemon` is resolved

```mermaid
flowchart LR
    A["resolved cfg.oAuth.daemon"] -->|true / 'true'| T["daemon = true"]
    A -->|false, unset or empty| F["daemon = false"]
    T --> U1["MailOAuthService.buildContext"]
    F --> U1
    T --> U2["TenantMailService.createGraphMailSender"]
    F --> U2
```

There is no fallback from a tenant block to the global `grails.mail.oAuth.daemon`. The default
tenant resolves to the global `grails.mail` block itself, so for it `cfg.oAuth.daemon` *is* the
global value. Any other tenant must set `daemon = true` explicitly to run app-only.

### 5.4 Consent / authorization flow via the controller

```mermaid
sequenceDiagram
    participant Admin as Admin browser
    participant C as MailOAuthController
    participant OAS as MailOAuthService
    participant ST as SessionStateStoreService
    participant MS as Entra ID

    Admin->>C: GET /mailOAuth/generate
    C->>C: TenantIdContext.set(tenantContextProvider id)
    C->>OAS: generateAuthCodeURL()
    OAS->>OAS: cfg = resolve(tenantId), ctx = buildContext
    OAS->>ST: storeState(clientId, state = tenantId + nonce)
    alt ctx.daemon
        OAS-->>C: adminconsent URL (tenant_id, client_id, state, redirect)
    else interactive
        OAS-->>C: authorize URL (scope, state, prompt=login)
    end
    C-->>Admin: 302 to Microsoft
    Admin->>MS: sign in / grant consent
    MS-->>Admin: 302 to callback_url with state plus code or admin_consent=True
    Admin->>C: GET /mailOAuth/callback
    C->>OAS: generateAccessToken(code, state, params, admin_consent)
    OAS->>OAS: tenantId = parseState(state), ctx = buildContext
    OAS->>ST: verify clientId matches stored state
    alt ctx.daemon
        OAS->>MS: authStrategy.acquireToken (secret or JWT assertion)
    else interactive
        OAS->>MS: getAccessToken(code), validate token user matches username
    end
    OAS->>OAS: tokenStore.saveToken(tenantId, token)
    OAS-->>C: redirect.uri
    C-->>Admin: 302 /mailOAuth/index
```

If admin consent was granted outside the app (portal or CLI), the controller step is unnecessary for
daemon tenants: the first send finds no token, and `getAccessToken` → `refreshAccessToken` acquires
one lazily.

### 5.5 Refresh and revoke

```mermaid
flowchart TD
    GA["getAccessToken()<br/>tenantId from TenantIdContext"] --> T{"token present<br/>and not expired?"}
    T -- yes --> RET["return token"]
    T -- no --> RF["refreshAccessToken(tenantId, old)"]
    RF --> B["buildContext(resolve(tenantId))"]
    B --> D{"ctx.daemon ?"}
    D -- yes --> AQ["authStrategy.acquireToken(oauthService)"]
    D -- no --> RT["authStrategy.refreshToken(oauthService, old.refreshToken)"]
    AQ --> SV["tokenStore.saveToken(tenantId)"]
    RT --> SV
    SV --> RET
```

`revokeToken()` clears the tenant's entry in `MemoryTokenStore` first, then best-effort revokes the
refresh and access tokens at Microsoft. For daemon tenants there is no refresh token, so revoke is
effectively a local clear.

---

## 6. Per-tenant isolation

| Resource | Owner | Key | Lifetime |
|---|---|---|---|
| OAuth token | `MemoryTokenStore` | `Long tenantId` | Until expiry/revoke; lost on restart |
| OAuth state | `SessionStateStoreService` | `clientId` | Until callback |
| Graph client | `TenantGraphClientRegistryService` | `tenantId` + signature of `enabled`, `graph.enabled`, `client_id`, `tenant_id` | Rebuilt under a per-tenant lock when the signature changes |
| Thread pool | `TenantMailExecutorRegistry` | `tenantId` | Created on first send with `poolSize` (default 5); idle threads time out; all shut down on context destroy |
| `TenantOAuthContext` / auth strategy | `MailOAuthService.buildContext` | none | Per call, never cached |
| Certificate keystore | `CertificateStrategy` | none | Re-read from `certificateBase64` or the file at `certificatePath` on every token acquisition (rotation without restart) |

A tenant's slow or failing mailbox cannot block another tenant's queue, and a token for tenant A is
never presented for tenant B because the tenant id travels with the task via
`TenantAwareExecutorService`.

---

## 7. Reader side (optional)

The reader is independent of sending and is driven by a `GraphConfig` object the caller builds
(`tenantId`, `graphTenantId`, `clientId`, `secretId` or certificate fields, `emailAddress`,
`daemon`, `isShared`).

```mermaid
flowchart TD
    R["GraphEmailReaderService.listMessages(graphConfig)"] --> TS["readerTokenStoreService.getTokenFor(graphConfig)"]
    TS --> Q{"daemon or useCertAuth ?"}
    Q -- yes --> AQ["auto-acquire via GraphAuthStrategyFactory.build(graphConfig)"]
    Q -- no --> IT["token from /readerToken/callback (interactive)"]
    AQ --> U{"daemon, useCertAuth,<br/>or shared mailbox ?"}
    IT --> U
    U -- yes --> UB["/users/{emailAddress}/..."]
    U -- no --> ME["/me/..."]
```

---

## 8. Configuration shapes

**Single tenant / default only** (unchanged from earlier versions):

```groovy
DEFAULT_TENANT_ID = 1L
grails.mail.oAuth.enabled = true
grails.mail.oAuth.graph.enabled = true
grails.mail.oAuth.daemon = false
grails.mail.oAuth.client_id = '<app id>'
grails.mail.oAuth.secret_val = '<externalized secret>'
grails.mail.oAuth.tenant_id = '<entra directory id>'
grails.mail.oAuth.callback_url = 'https://host/mailOAuth/callback'
grails.mail.username = 'mailbox@company.com'
```

**Mixed tenants**:

```groovy
DEFAULT_TENANT_ID = 1L
grails.mail.oAuth.daemon = false                 // applies to tenant 1 (default) only

tenant_42.grails.mail.oAuth.enabled = true       // app-only with certificate
tenant_42.grails.mail.oAuth.graph.enabled = true
tenant_42.grails.mail.oAuth.daemon = true
tenant_42.grails.mail.oAuth.useCertAuth = true
tenant_42.grails.mail.oAuth.certificatePath = '/etc/app/tenant42.pfx'
tenant_42.grails.mail.oAuth.client_id = '<app id 42>'
tenant_42.grails.mail.oAuth.tenant_id = '<entra id 42>'
tenant_42.grails.mail.username = 'noreply@tenant42.com'

tenant_43.grails.mail.oAuth.enabled = true       // delegated with client secret
tenant_43.grails.mail.oAuth.graph.enabled = true
tenant_43.grails.mail.oAuth.client_id = '<app id 43>'
tenant_43.grails.mail.oAuth.secret_val = '<externalized secret 43>'
tenant_43.grails.mail.oAuth.tenant_id = '<entra id 43>'
tenant_43.grails.mail.username = 'support@tenant43.com'

tenant_44.grails.mail.host = 'smtp.tenant44.com' // plain SMTP, no oAuth block
tenant_44.grails.mail.username = 'app@tenant44.com'
tenant_44.grails.mail.password = '<externalized>'
```

Secrets and PFX passwords must come from environment or a vault, never from tracked files.

---

## 9. Known limitations

- Tokens, OAuth state and thread pools are in-memory: a multi-node deployment needs sticky routing
  for the consent callback, and every node acquires its own tokens.
- `callback_url`, `api_scope`, `redirect.uri`, Graph HTTP timeouts and `attachmentMax` are global,
  not per tenant.
- The Graph client cache signature ignores `daemon`, `useCertAuth` and certificate fields; this is
  harmless because the token credential and keystore are resolved on every call, but a signature
  change is the only thing that rebuilds the client.
- The interactive flow validates that the signed-in user matches `username`; daemon flows rely on
  `username`/`from` being a mailbox the application has `Mail.Send` rights on.
