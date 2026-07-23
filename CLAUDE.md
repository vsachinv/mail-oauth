# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`mail-oauth` is an **Apache Grails 7.0.14 plugin** (not an application) that lets PV Suite Grails apps send and read mail through Microsoft OAuth-secured channels instead of basic-auth SMTP. It layers OAuth 2.0 token management, multi-tenant mail configuration, and Microsoft Graph / IMAP email reading on top of the Grails Mail plugin (`org.grails.plugins:grails-mail:5.0.3`).

- Stack: Apache Grails 7.0.14, Groovy 4 (via BOM), **JDK 17** (hard requirement — `compileJava.options.release = 17`), Gradle 8.14.4 wrapper, Spring Boot 3.5.x, Jakarta EE.
- The Gradle plugin comes from a `buildscript {}` block (`org.apache.grails.gradle.grails-plugin`), and versions are managed by `org.apache.grails:grails-bom`. Core module groups differ: `org.apache.grails`, `org.apache.grails.web`, `org.apache.grails.views`.
- OAuth is done with ScribeJava (`MicrosoftAzureActiveDirectory20Api`); Graph calls use the `com.microsoft.graph:microsoft-graph:6.+` SDK.
- Published as `org.grails.plugins:mail-oauth:<version>` to RxLogix Nexus. Version lives in `gradle.properties` (currently `7.6.0-M1`, the first Grails 7 migration build line).

## Commands

The machine default `java` is Java 8 — builds **require JDK 17** (e.g. sdkman `17.0.6-zulu`). `gradlew` may lack the execute bit; invoke via `sh`:

```bash
sh ./gradlew clean build            # build + test, JAR in build/libs/
sh ./gradlew clean build -x test    # build without tests
sh ./gradlew test                   # run all tests (Spock, JUnit Platform)
sh ./gradlew test --tests "grails.plugins.mail.oauth.TenantMailServiceSpec"   # single test class
sh ./gradlew compileGroovy --stacktrace   # quick compile check
sh ./gradlew publish                # publish JAR + sources JAR to Nexus (needs nexus* creds)
```

Nexus credentials come from `nexusUsername`/`nexusPassword`/`nexusUrl` gradle properties or `NEXUS_USERNAME`/`NEXUS_PASSWORD`/`NEXUS_URL` env vars (see README).

## Source layout — two roots

- `grails-app/` — Grails artefacts wired into the Spring context by convention: `controllers/`, `services/` (auto-injected singletons), `conf/plugin.groovy` (default config), `views/`.
- `src/main/groovy/` — plain Groovy classes (senders, token stores, Graph/IMAP clients, tenant infrastructure) instantiated explicitly, mostly as Spring beans in the plugin descriptor.

## Architecture

**Everything is multi-tenant.** A `Long tenantId` threads through nearly every call. The plugin does **not** know how to obtain the current tenant — the consuming app must supply a Spring bean named `tenantContextProvider` implementing `grails.plugins.tenant.TenantContextProvider` (`Long getCurrentTenantId()`). `TenantIdContext` is a ThreadLocal used to carry the tenant id into deeper layers (senders, token refresh) without threading it as a parameter; controllers set it and clear it in a `finally`.

**Bean wiring** happens in `src/main/groovy/grails/plugins/mail/oauth/MailOauthGrailsPlugin.groovy#doWithSpring`. This is the map of the plugin — read it first. Reader beans (`graphEmailReaderService`, `imapEmailReaderService`, token stores) are only registered when `grails.mail.reader.enabled` (and the respective `graph`/`imap` sub-flag) is true.

**Sending flow** (`TenantMailService` is the public entry point):
1. `sendMail { … }` / `sendMailWithTenant(tenantId) { … }` resolves the tenant's config, then `resolveBuilder` picks one of three strategies based on config:
   - `oAuth.enabled && oAuth.graph.enabled` → **Graph API** send (`GraphMailSenderImpl`, via `graphMailMessageBuilderFactory`).
   - `oAuth.enabled` only → **OAuth SMTP** (`OAuthMailSenderImpl`).
   - neither → **plain SMTP** via the standard Grails Mail `MailMessageBuilderFactory`.
2. The message is dispatched on a **per-tenant thread pool** from `TenantMailExecutorRegistry` (`TenantAwareExecutorService` propagates the ThreadLocal tenant id into worker threads — this was the fix for an executor multi-threading bug; don't reintroduce raw executors).

**Config resolution** (`TenantMailConfigResolverService.resolve(tenantId)`): if `tenantId == config.DEFAULT_TENANT_ID`, use org-level `grails.mail`; otherwise read `tenant_<id>.grails.mail`. In both cases host/port/props from the global `grails.mail` are merged in as defaults (tenant values win). Returns a `ConfigObject`; throws if no config found.

**Token management** (`MailOAuthService`): generate auth-code URL → exchange code for token in `callback` → store in `MemoryTokenStore` (in-memory `ConcurrentHashMap<Long, OAuthToken>` keyed by tenant). `getAccessToken()` auto-refreshes when expired. Supports both interactive (auth code) and `daemon` (client-credentials / admin-consent) flows — `daemon` is driven by `grails.mail.oAuth.daemon`. OAuth `state` is encoded with the tenant id (`MailOAuthUtil.buildOAuthState`/`parseState`) so the stateless callback can recover the tenant.

**Graph client caching** (`TenantGraphClientRegistryService`): caches one `GraphApiClient` per tenant, keyed by a signature of the OAuth config; rebuilds under a per-tenant `ReentrantLock` when the signature changes.

**Reader side** (optional, independent of sending): `GraphEmailReaderService` and `ImapEmailReaderService` read mail; `ReaderTokenController` handles a separate OAuth callback (`/readerToken/callback`) with its own `readerTokenStoreService`. See the `examples/` folder for runnable usage scripts.

**Controller** `MailOAuthController` (`/mailOAuth/*`): `index`, `generate`, `callback`, `refresh`, `revoke`, `tokenStatus`, `sendTestMail` drive the consent/authorization flow. Config defaults are in `grails-app/conf/plugin.groovy`; consuming apps override via their own config. Static config accessors live in `MailOAuthUtil` (reads `Holders.config`).

## Conventions

- Commit messages follow `PVCM-<ticket> : <message>` (Jira ticket prefix). The current work branch is `PVCM-128572`; PRs target `master`.
- Log lines are tagged with bracketed prefixes like `[GRAPH_EMAIL] [SEND_EMAIL] [STARTED]` — match this style.
- `GraphMailSenderImpl.doSend()` deliberately throws; Graph sends must go through `sendMailViaGraph`.

## Security note (repo currently violates this)

`grails-app/conf/plugin.groovy` and files under `examples/` contain **real-looking Microsoft client IDs and client secrets** committed to source control. These are secrets and should be externalized per-environment and rotated. Do not add new secrets to tracked files, and flag/rotate the existing ones rather than copying them into new code.