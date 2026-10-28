# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Build the plugin JAR
./gradlew assemble

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "grails.plugins.mail.oauth.MailOAuthServiceSpec"

# Publish to local Maven repo
./gradlew publishToMavenLocal

# Publish to Nexus (requires NEXUS_USERNAME, NEXUS_PASSWORD, NEXUS_URL env vars)
./gradlew publish
```

Requires Java 11. Tests use JUnit Platform (`useJUnitPlatform()`).

## Architecture

This is a Grails 6 plugin (`org.grails.plugins:mail-oauth`) that extends the `grails-mail` plugin (v4.0+) to add Microsoft OAuth / Graph API support for both sending and reading email.

**Two send paths:**
- **Graph API sender** (`grails.mail.oAuth.graph.enabled: true`): `GraphMailSenderImpl` → `GraphApiClient` (wraps `microsoft-graph` SDK's `GraphServiceClient`) → Microsoft Graph REST API. Used for sending via Graph instead of SMTP.
- **Turning mail off**: `grails.mail.disabled = true` is enforced by Grails Mail's own `MailService.sendMail` (logs and returns `null`); the plugin does not bypass it on this line.
- **OAuth SMTP sender** (`grails.mail.oAuth.enabled: true`, graph disabled): `OAuthMailSenderImpl` wraps the standard JavaMail sender but injects an OAuth2 bearer token as the SMTP password, obtained via `MailOAuthService`.

**Two read paths:**
- **Graph reader** (`grails.mail.reader.enabled: true`): `GraphEmailReaderService` uses the same `GraphApiClient` to list/move/delete messages and manage folders via the Graph API.
- **IMAP reader**: `ImapEmailReaderService` opens a standard IMAP `Store` connection, using an OAuth token as the password when `ImapConfig.oAuthEnabled` is true.

**OAuth token lifecycle (`MailOAuthService`):**
- Uses ScribeJava (`com.github.scribejava`) against `MicrosoftAzureActiveDirectory20Api`.
- Two modes: interactive (authorization-code flow with `/mailOAuth/generate` → `/mailOAuth/callback`) and **daemon** (client-credentials grant, no user interaction).
- Tokens stored in `MemoryTokenStore`; refreshed automatically when expired.

**Reader token lifecycle (`ReaderTokenStoreService` / `InMemoryReaderTokenStoreService`):**
- Each reader config (`GraphConfig`) has its own token, managed separately from the sender token.
- OAuth2 authorization for readers goes through `ReaderTokenController` → `/readerToken/callback`.

**Spring bean wiring** happens in `MailOauthGrailsPlugin.doWithSpring()`. Key beans: `mailOAuthService`, `tokenStore`, `stateStoreService`, `graphApiClient`, `graphEmailReaderService`, `imapEmailReaderService`. The plugin reacts to config changes via `onConfigChange` to re-register the `mailSender` bean without restart.

**`GraphApiClient` caching:** maintains a `ConcurrentHashMap` of `GraphServiceClient` instances keyed by `GraphConfig.configName` to avoid exhausting socket ports when multiple reader configs exist.

## Key Config Properties

All under `grails.mail` in `application.yml`:

| Property | Purpose |
|---|---|
| `oAuth.enabled` | Master switch for OAuth send |
| `oAuth.graph.enabled` | Use Graph API sender (vs OAuth SMTP) |
| `oAuth.daemon` | Client-credentials flow (no user login needed) |
| `oAuth.useCertAuth` | Use a PFX certificate (signed JWT client assertion) instead of `secret_val`; requires `daemon: true` |
| `oAuth.certificatePath / certificatePassword / certificateAlias` | PFX keystore location, password and key alias for certificate auth |
| `oAuth.certificateBase64` | PFX bytes base64 encoded, alternative to `certificatePath` (wins when both set); never log it |
| `oAuth.client_id / secret_val / tenant_id` | Azure AD app credentials |
| `oAuth.api_scope` | OAuth scopes |
| `oAuth.graph.attachmentMax` | Max attachment size in MB (Graph sender) |
| `oAuth.graph.http.*Timeout` | OkHttp timeouts in seconds |
| `reader.enabled` | Enable mail reading features |
| `reader.graph.callbackUrl` | Callback URL for reader OAuth flow |

## Package Structure

```
grails.plugins.mail.oauth        — MailOAuthService, MailOauthGrailsPlugin, token model
grails.plugins.mail.oauth.sender — OAuthMailSenderImpl (SMTP + OAuth)
grails.plugins.mail.oauth.token  — TokenStore interface, MemoryTokenStore, OAuthToken
grails.plugins.mail.graph        — GraphApiClient, GraphConfig, GraphMessage, ImmutableIdHeaderInterceptor
grails.plugins.mail.graph.auth   — GraphAuthStrategy, SecretKeyStrategy, CertificateStrategy, GraphAuthStrategyFactory
grails.plugins.mail.graph.sender — GraphMailSenderImpl, GraphMailMessageBuilder
grails.plugins.mail.graph.reader — GraphEmailReaderService
grails.plugins.mail.graph.token  — ReaderTokenStoreService, InMemoryReaderTokenStoreService, GraphConfig credentials
grails.plugins.mail.imap         — ImapConfig, ImapEmailReaderService
```

Controllers (`grails-app/controllers`): `MailOAuthController` (sender OAuth flow), `ReaderTokenController` (reader OAuth flow).