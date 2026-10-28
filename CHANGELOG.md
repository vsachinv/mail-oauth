# Changelog

All notable changes to the `mail-oauth` plugin are documented here.
Versions correspond to the `version` property in `gradle.properties` and the artifacts published to RxLogix Nexus as `org.grails.plugins:mail-oauth:<version>`.

## 7.6.0-M3 — 2026-10-23

### Added
- **Certificate-based authentication** (PVR-107197) for Microsoft Entra ID token acquisition as an alternative to a client secret, for both the Graph/SMTP sender and the Graph/IMAP reader. A PFX (PKCS#12) private key signs an RS256 JWT client assertion that is posted directly to the token endpoint. Signing uses the JDK `java.security.Signature` API, so **no new dependencies** were introduced.
- New `GraphAuthStrategy` abstraction (`grails.plugins.mail.graph.auth`) with `SecretKeyStrategy` (existing ScribeJava flow) and `CertificateStrategy`, selected by `GraphAuthStrategyFactory`. `TenantOAuthContext` now carries the resolved strategy.
- New per-tenant sender config keys under `grails.mail.oAuth` / `tenant_<id>.grails.mail.oAuth`: `useCertAuth`, `certificatePath`, `certificatePassword`, `certificateAlias`. Matching fields on `GraphConfig` for the reader. Tenants may mix credential types.
- `certificateBase64` (sender config and `GraphConfig`): the PFX keystore bytes base64 encoded, as an alternative to `certificatePath` for vault/environment-injected secrets. Takes precedence over the path; strict decoding; never logged.
- `SETUP_GUIDE.md` with the Entra ID registration, permission, admin-consent, certificate creation and rotation steps; `design_certificate.md` with the strategy design.
- Tests: `CertificateStrategySpec` (JWT header/claims/`x5t`/signature, factory selection) and `AuthModeConfigurationSpec` (real ScribeJava construction for every auth mode on the multi-tenant sender and the reader).

### Changed
- **`daemon` is now resolved per tenant**: `tenant_<id>.grails.mail.oAuth.daemon` is read from the tenant's own config; unset or empty means `false`, with no fallback to the global `grails.mail.oAuth.daemon` (`MailOAuthUtil.isDaemon(ConfigObject)`). The default tenant still uses the global `grails.mail` block. Individual tenants can choose app-only or interactive auth. Used by `MailOAuthService.buildContext` and the Graph sender created in `TenantMailService`.
- Certificate auth is app-only and therefore requires the tenant's effective `daemon=true`; `buildContext` rejects `useCertAuth` without `daemon` with an `IllegalStateException`.
- ScribeJava `OAuth20Service` is built without `apiSecret` when no secret is configured (ScribeJava rejects an empty secret), so certificate-only tenants can still use revoke and the admin-consent callback.
- `GraphEmailReaderService` and `InMemoryReaderTokenStoreService` treat `useCertAuth` like `daemon` (app-only `users/{email}` access, automatic token acquisition).

### Compatibility notes
- No behaviour change for existing secret-based tenants (interactive or daemon). `GraphConfig` `serialVersionUID` is unchanged.

## 7.6.0-M2 — 2026-09-16

### Changed
- Upgraded Apache Grails from **7.0.14** to **7.0.16** (`grailsVersion` in `gradle.properties`). This moves the Grails BOM, Gradle plugin, and all `org.apache.grails*` core modules to 7.0.16.
- Replaced the Grails Mail plugin dependency `org.grails.plugins:grails-mail:5.0.3` with the Apache-released **`org.apache.grails:grails-mail:7.0.16`**. The dependency is declared without a version because `grails-bom` (via `grails-base-bom`) manages it, so it always matches `grailsVersion`.
  - Package name (`grails.plugins.mail`) and the public API of `MailMessageBuilder`, `MailMessageBuilderFactory`, `MailConfigurationProperties`, `MailMessageContentRenderer`, and `MailService` are unchanged, so no plugin code changes were required.
  - Transitive mail stack now resolves to `jakarta.mail-api` 2.1.5, `org.eclipse.angus:jakarta.mail` 2.0.5, and `angus-activation` 2.0.3.
- Plugin descriptor `dependsOn` constraint for the `mail` plugin raised from `* > 5.0.0` to `* > 7.0.0`.
- README and CLAUDE.md updated to reflect the new Grails and Grails Mail coordinates.

### Compatibility notes
- Consuming applications should be on Apache Grails **7.0.16**. Running against 7.0.14 will pull `grails-core` 7.0.16 transitively via `grails-mail` and may cause version conflicts.
- The `grails-mail` artifact is managed by `grails-bom` 7.0.16 (through the imported `grails-base-bom`), so consuming apps do not need to pin its version.
- JDK 17 remains a hard requirement.

## 7.6.0-M1

First Grails 7 build line.

### Changed
- Migrated the plugin from Grails 6.2.0 / JDK 11 to Apache Grails 7.0.14 / JDK 17 (Spring Boot 3.5.x, Groovy 4, Jakarta EE).
- Core dependency groups moved to `org.apache.grails`, `org.apache.grails.web`, and `org.apache.grails.views`.
- Grails Mail plugin moved from `org.grails.plugins:mail:4.0.0` (javax.mail) to `org.grails.plugins:grails-mail:5.0.3` (jakarta.mail).
- All `javax.*` EE imports replaced with `jakarta.*`.
- Upgraded dependent libraries (ScribeJava 8.3.3, Microsoft Graph SDK 6.x, commons-io 2.19.0, reactor-core 3.8.x).

### Fixed
- Tenant context leak: `TenantIdContext` ThreadLocal is now cleared reliably after each request/dispatch.

### Added
- Spock unit test coverage for tenant-aware mail sending (`TenantMailServiceSpec` and related specs).
- `docs/MULTI_TENANCY.md` describing the multi-tenant architecture, linked from the README.

## 7.5-JDK11-1.0-M10

Last Grails 6.x (JDK 11, `javax.*`) release. Maintenance only.
