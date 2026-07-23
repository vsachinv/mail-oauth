# mail-oauth

A Grails plugin that adds the capability to send emails using the Microsoft Graph API and provides Microsoft OAuth support for SMTP/IMAP connections. It is built on top of the Grails Mail plugin APIs and adds OAuth 2.0 token management, multi-tenant mail configuration, and optional email-reading (Graph and IMAP) support so PV Suite applications can send and read mail through modern, OAuth-secured Microsoft endpoints.

## About

| Detail              | Value                                                     |
|---------------------|-----------------------------------------------------------|
| Framework           | Apache Grails 7.0.14 (Spring Boot 3.5.x, Jakarta EE)     |
| JDK                 | 17                                                        |
| Groovy              | 4 (managed by `org.apache.grails:grails-bom`)            |
| Group               | `org.grails.plugins`                                      |
| Artifact            | `mail-oauth`                                              |
| Current Version     | Refer to `gradle.properties` (e.g. `7.6.0-M1`)           |
| Repository          | RxLogix Nexus                                             |

> **Grails 7 upgrade note:** This plugin was migrated from Grails 6.2.0 / JDK 11 to Apache Grails 7.0.14 / JDK 17. The Maven group of core dependencies moved to `org.apache.grails`, the Grails Mail plugin is now `org.grails.plugins:grails-mail` (5.x, Jakarta Mail based), and all `javax.*` EE imports became `jakarta.*`.

## Version Compatibility

Pick the plugin version that matches your host application's Grails major version. The two lines are **not** interchangeable — the Grails 7 line requires JDK 17 and Jakarta EE, while the Grails 6 line requires JDK 11 and Java EE (`javax.*`).

| Plugin version         | Grails    | JDK | Groovy   | Servlet / Mail namespace | Grails Mail plugin                 | Status          |
|------------------------|-----------|-----|----------|--------------------------|------------------------------------|-----------------|
| `7.6.0-M1` (current)   | 7.0.14    | 17  | 4.x      | Jakarta EE (`jakarta.*`) | `org.grails.plugins:grails-mail` 5.x | Active (Grails 7) |
| `7.5-JDK11-1.0-M10`    | 6.2.0     | 11  | 3.0.23   | Java EE (`javax.*`)      | `org.grails.plugins:mail` 4.0.0    | Maintenance (last Grails 6.x) |

**For a Grails 6.x application**, use the last 6.x-compatible release instead of the current one:

```groovy
dependencies {
    implementation "org.grails.plugins:mail-oauth:7.5-JDK11-1.0-M10"  // Grails 6.2.0 / JDK 11
}
```

> The `7.5-JDK11-1.0-M10` line remains available on Nexus for existing Grails 6 consumers but only receives critical fixes; new development targets the Grails 7 line.

## Purpose

This plugin enables PV Suite Grails applications to send and read email through Microsoft OAuth-secured channels instead of basic-auth SMTP. It provides:

- **OAuth-based mail sending** -- send mail over OAuth-secured SMTP or directly via the Microsoft Graph API, layered on top of the Grails Mail plugin's `sendMail` API.
- **Token management** -- generation, refresh, revocation, and in-memory storage of OAuth access/refresh tokens, including a scheduled refresh based on configurable frequency and expiry thresholds.
- **Multi-tenant support** -- per-tenant mail configuration resolution, tenant-scoped Graph clients, and tenant-aware token storage.
- **Email reading (optional)** -- Microsoft Graph and IMAP email reader services that can be enabled independently of the sender.
- **Controller endpoints** -- a `MailOAuthController` exposing index, generate, refresh, revoke, callback, token-status, and test-mail actions to drive the OAuth consent/authorization flow.
- **Default configuration** -- sensible defaults in `plugin.groovy` that consuming applications can override.

## Prerequisites

- JDK 17
- Gradle 8.x (wrapper included — Gradle 8.14.4)
- Access to the RxLogix Nexus repository for dependencies and publishing
- A registered Microsoft Entra ID (Azure AD) application with client ID, client secret, and the required API scopes

## Building the Plugin

```bash
# Clean and build the plugin JAR
./gradlew clean build

# Build without running tests
./gradlew clean build -x test
```

The output JAR is generated under `build/libs/`.

## Publishing to Nexus

The plugin uses the `maven-publish` Gradle plugin to publish artifacts to the RxLogix Nexus repository. The main JAR is published along with a sources JAR.

### Nexus Credentials Setup

Provide credentials via one of the following methods:

**Option 1 -- `gradle.properties` (user home: `~/.gradle/gradle.properties`)**

```properties
nexusUsername=<your-username>
nexusPassword=<your-password>
nexusUrl=<your-nexus-repository-url>
```

**Option 2 -- Environment variables**

```bash
export NEXUS_USERNAME=<your-username>
export NEXUS_PASSWORD=<your-password>
export NEXUS_URL=<your-nexus-repository-url>
```

> Note: the publish configuration sets `allowInsecureProtocol = true`, so an HTTP (non-TLS) Nexus URL is accepted. Prefer HTTPS where available.

### Publish Command

```bash
./gradlew publish
```

This publishes the following artifacts to Nexus:

- `org.grails.plugins:mail-oauth:<version>` (main JAR)
- `org.grails.plugins:mail-oauth:<version>:sources`

## Using this Plugin as a Gradle Dependency

In your consuming Grails application's `build.gradle`:

```groovy
repositories {
    maven {
        url "<your-nexus-repository-url>"
    }
}

dependencies {
    implementation "org.grails.plugins:mail-oauth:7.6.0-M1"  // refer to latest version from gradle.properties / Nexus
}
```

This plugin depends on the Grails Mail plugin (`org.grails.plugins:grails-mail` 5.0.0+, Jakarta Mail based), which is loaded automatically.

## Controller Endpoints

The plugin exposes `MailOAuthController` under the `/mailOAuth` URI:

| Action        | URI                       | Description                                              |
|---------------|---------------------------|---------------------------------------------------------|
| `index`       | `/mailOAuth/index`        | Landing/status page for the OAuth flow.                 |
| `generate`    | `/mailOAuth/generate`     | Redirects to the Microsoft authorization URL.           |
| `callback`    | `/mailOAuth/callback`     | OAuth redirect endpoint; exchanges the code for a token.|
| `refresh`     | `/mailOAuth/refresh`      | Refreshes the access token for the current tenant.      |
| `revoke`      | `/mailOAuth/revoke`       | Revokes the stored token.                               |
| `tokenStatus` | `/mailOAuth/tokenStatus`  | Reports validity/expiry of the current access token.    |
| `sendTestMail`| `/mailOAuth/sendTestMail` | Sends a test email to a supplied address.               |

## Configuration

The plugin ships with default configuration in `grails-app/conf/plugin.groovy`. Consuming applications can override any of these properties in their own `application.groovy` or `application.yml`.

| Property                                        | Description                                          | Default                                            |
|-------------------------------------------------|------------------------------------------------------|----------------------------------------------------|
| `grails.mail.oAuth.enabled`                     | Enables OAuth mail support                            | `false`                                            |
| `grails.mail.oAuth.client_id`                   | Microsoft Entra ID application (client) ID           | *(configure per environment)*                      |
| `grails.mail.oAuth.secret_val`                  | Client secret                                        | *(configure per environment -- do not commit)*     |
| `grails.mail.oAuth.api_scope`                   | OAuth scopes requested                               | `https://outlook.office.com/SMTP.Send offline_access` |
| `grails.mail.oAuth.callback_url`                | OAuth redirect/callback URL                          | `http://localhost:9090/mailOAuth/callback`         |
| `grails.mail.oAuth.tenant_id`                   | Microsoft tenant ID                                  | `common`                                           |
| `grails.mail.oAuth.token.refresh.frequency`     | Token refresh check frequency (seconds)              | `300`                                              |
| `grails.mail.oAuth.token.refresh.time.difference`| Refresh if token expires within (seconds)           | `600`                                              |
| `grails.mail.oAuth.redirect.uri`                | URI to redirect to after controller actions          | `/mailOAuth/index`                                 |
| `grails.mail.oAuth.graph.enabled`               | Send mail via Microsoft Graph API instead of SMTP    | `false`                                            |
| `grails.mail.oAuth.graph.attachmentMax`         | Maximum attachment size (MB)                          | `3`                                                |
| `grails.mail.oAuth.health.check.disabled`       | Disables the OAuth health check                       | `false`                                            |
| `grails.mail.reader.enabled`                    | Enables the email reader support                      | `false`                                            |
| `grails.mail.reader.health.check.disabled`      | Disables the reader health check                      | `false`                                            |

> **Security:** Never commit real client secrets to source control. Provide `grails.mail.oAuth.secret_val` (and `client_id`) through externalized/environment configuration per deployment, and rotate any secret that has been committed.

## Multi-Tenancy

The plugin is multi-tenant end to end: a single application instance can send and read mail for many tenants, each with its own Microsoft Entra ID application, credentials, mailbox, and OAuth tokens. Every send, token operation, and reader call is scoped by a **tenant id**.

> ⚠️ **Two different "tenants" — don't confuse them:**
> - **Application tenant id** — a `Long` that identifies *your* tenant (organization/customer) within the host application. This is what the plugin partitions everything by.
> - **`grails.mail.oAuth.tenant_id`** — the *Microsoft Entra ID (Azure AD)* directory id (or `common`) used when talking to Microsoft. It is one property *inside* a tenant's mail config.

### 1. Supply a `TenantContextProvider`

The plugin does not know how your application determines "the current tenant". You must register a Spring bean named **`tenantContextProvider`** implementing `grails.plugins.tenant.TenantContextProvider`:

```groovy
package com.example

import grails.plugins.tenant.TenantContextProvider

class AppTenantContextProvider implements TenantContextProvider {
    @Override
    Long getCurrentTenantId() {
        // resolve from your security context / request / thread-local, e.g.:
        return SecurityContext.currentUser?.tenantId
    }
}
```

```groovy
// grails-app/conf/spring/resources.groovy (in the consuming application)
beans = {
    tenantContextProvider(com.example.AppTenantContextProvider)
}
```

Internally the resolved id is carried into deeper layers (senders, token refresh, worker threads) via the `TenantIdContext` thread-local, so you do not pass it explicitly on every call.

### 2. Per-tenant configuration resolution

`TenantMailConfigResolverService.resolve(tenantId)` resolves the mail config for a tenant as follows:

- If `tenantId == grails.DEFAULT_TENANT_ID`, the **organization-level** config at `grails.mail` is used (the existing, single-tenant style config).
- Otherwise, **tenant-specific** config is read from a top-level `tenant_<tenantId>` block, i.e. `tenant_<tenantId>.grails.mail`.
- In both cases, `host` / `port` / `props` from the global `grails.mail` are merged in as defaults; tenant/org values win.
- If no config is found for the tenant, an `IllegalStateException` is thrown.

Example — two tenants, each with its own Entra ID app and mailbox:

```groovy
// application.groovy (consuming application)
grails.DEFAULT_TENANT_ID = 1

// Organization-level config (used when current tenantId == DEFAULT_TENANT_ID)
grails.mail.username = 'shared@org.com'
grails.mail.oAuth.enabled = true
grails.mail.oAuth.graph.enabled = true
grails.mail.oAuth.client_id = '...'
grails.mail.oAuth.secret_val = '...'      // externalize; do not commit
grails.mail.oAuth.tenant_id = '<entra-directory-id>'

// Tenant-specific config for application tenant 42
tenant_42.grails.mail.username = 'noreply@tenant42.com'
tenant_42.grails.mail.oAuth.enabled = true
tenant_42.grails.mail.oAuth.graph.enabled = true
tenant_42.grails.mail.oAuth.client_id = '...'
tenant_42.grails.mail.oAuth.secret_val = '...'
tenant_42.grails.mail.oAuth.tenant_id = '<tenant42-entra-directory-id>'
```

### 3. Per-tenant runtime isolation

| Concern | Component | Isolation |
|---------|-----------|-----------|
| OAuth tokens | `MemoryTokenStore` | `ConcurrentHashMap<Long, OAuthToken>` keyed by tenant id |
| Microsoft Graph client | `TenantGraphClientRegistryService` | one cached `GraphApiClient` per tenant, keyed by a signature of the tenant's OAuth config; rebuilt under a per-tenant lock when config changes |
| Mail dispatch threads | `TenantMailExecutorRegistry` | one thread pool per tenant; worker threads are tenant-aware so the thread-local tenant id propagates into async sends |

### 4. Sending mail

`TenantMailService` is the public entry point and mirrors the Grails Mail `sendMail` DSL:

```groovy
class SomeService {
    def tenantMailService   // auto-wired

    void notifyCurrentTenant(String to) {
        // Uses tenantContextProvider.getCurrentTenantId() automatically
        tenantMailService.sendMail {
            to to
            subject 'Hello'
            body 'Sent through the tenant-resolved channel (Graph / OAuth-SMTP / SMTP).'
        }
    }

    void notifySpecificTenant(Long tenantId, String to) {
        // Explicit tenant — useful for background jobs with no request context
        tenantMailService.sendMailWithTenant(tenantId) {
            to to
            subject 'Hello'
            body 'Sent on behalf of a specific tenant.'
        }
    }
}
```

Based on the resolved tenant config, `TenantMailService` selects the send strategy automatically:

| Tenant config | Strategy |
|---------------|----------|
| `oAuth.enabled` **and** `oAuth.graph.enabled` | Microsoft Graph API send |
| `oAuth.enabled` only | OAuth-secured SMTP (`XOAUTH2`) |
| neither | Plain SMTP (standard Grails Mail) |

The `MailOAuthController` endpoints (`generate`, `refresh`, `revoke`, `tokenStatus`, `sendTestMail`) are likewise tenant-aware — each resolves the current tenant via `tenantContextProvider` and operates on that tenant's tokens.

## License

APACHE
