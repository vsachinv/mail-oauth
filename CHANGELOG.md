# Changelog

All notable changes to the `mail-oauth` plugin are documented here.
Versions correspond to the `version` property in `gradle.properties` and the artifacts published to RxLogix Nexus as `org.grails.plugins:mail-oauth:<version>`.

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
