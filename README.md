# mail-oauth

A Grails plugin that adds the capability to send emails using the Microsoft Graph API and provides Microsoft OAuth support for SMTP/IMAP connections. It is built on top of the Grails Mail plugin APIs and adds OAuth 2.0 token management, multi-tenant mail configuration, and optional email-reading (Graph and IMAP) support so PV Suite applications can send and read mail through modern, OAuth-secured Microsoft endpoints.

## About

| Detail              | Value                                                     |
|---------------------|-----------------------------------------------------------|
| Framework           | Grails 6.2.0                                              |
| JDK                 | 11                                                        |
| Groovy              | 3.0.23                                                    |
| Group               | `org.grails.plugins`                                      |
| Artifact            | `mail-oauth`                                              |
| Current Version     | Refer to `gradle.properties`                              |
| Repository          | RxLogix Nexus                                             |

## Purpose

This plugin enables PV Suite Grails applications to send and read email through Microsoft OAuth-secured channels instead of basic-auth SMTP. It provides:

- **OAuth-based mail sending** -- send mail over OAuth-secured SMTP or directly via the Microsoft Graph API, layered on top of the Grails Mail plugin's `sendMail` API.
- **Token management** -- generation, refresh, revocation, and in-memory storage of OAuth access/refresh tokens, including a scheduled refresh based on configurable frequency and expiry thresholds.
- **Multi-tenant support** -- per-tenant mail configuration resolution, tenant-scoped Graph clients, and tenant-aware token storage.
- **Email reading (optional)** -- Microsoft Graph and IMAP email reader services that can be enabled independently of the sender.
- **Controller endpoints** -- a `MailOAuthController` exposing index, generate, refresh, revoke, callback, token-status, and test-mail actions to drive the OAuth consent/authorization flow.
- **Default configuration** -- sensible defaults in `plugin.groovy` that consuming applications can override.

## Prerequisites

- JDK 11
- Gradle (wrapper included)
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
    implementation "org.grails.plugins:mail-oauth:7.5-JDK11-1.0-M10"  // refer to latest version from gradle.properties / Nexus
}
```

This plugin depends on the Grails Mail plugin (`mail` 4.0.0+), which is loaded automatically.

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

## License

APACHE
