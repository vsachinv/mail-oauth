## Grails Mail OAuth plugin

This plugin adds the capability to send emails using the Microsoft Graph API and provides Microsoft OAuth
support for SMTP/IMAP connections. It is written on top of the grails-mail plugin APIs.

For Grails 6.x.x
```groovy
implementation "org.grails.plugins:mail-oauth:6.0-JDK11-1.0-M11"
```

### Features

- Send mail via Microsoft Graph (`oAuth.graph.enabled: true`) or via SMTP with an OAuth bearer token.
- Read mail via Microsoft Graph or IMAP with OAuth.
- Interactive (delegated, user sign-in) or daemon (app-only, client-credentials) token flows.
- App credential can be a **client secret** or a **certificate** (PFX, signed JWT client assertion).

### Quick configuration

Daemon send via Graph with a certificate:

```yaml
grails:
  mail:
    username: mailbox@company.com          # mailbox to send as (mandatory in daemon mode)
    oAuth:
      enabled: true
      daemon: true
      useCertAuth: true
      client_id: <azure-app-client-id>
      tenant_id: <azure-tenant-id>
      api_scope: https://graph.microsoft.com/.default
      callback_url: https://app.company.com/mailOAuth/callback
      certificatePath: /etc/app/mail-oauth.pfx
      certificatePassword: <pfx-password>
      certificateAlias: mailcert           # optional, first alias used if omitted
      graph:
        enabled: true
```

Replace the four certificate keys with `secret_val: <client-secret>` to use a client secret instead.

| Property (under `grails.mail.oAuth`) | Purpose |
|---|---|
| `enabled` | Master switch for OAuth send |
| `graph.enabled` | Send via Graph API instead of OAuth SMTP |
| `daemon` | App-only client-credentials flow, no user login |
| `useCertAuth` | Use a PFX certificate instead of `secret_val`; requires `daemon: true` |
| `certificatePath` / `certificatePassword` / `certificateAlias` | PFX keystore location, password and key alias |
| `client_id` / `tenant_id` / `secret_val` | Azure app credentials |
| `api_scope` | OAuth scopes |
| `callback_url` | Redirect URI registered in Azure, used for consent and interactive login |

The mail reader takes the same options per mailbox through a `GraphConfig` object
(`daemon`, `useCertAuth`, `certificatePath`, `certificatePassword`, `certificateAlias`, `emailAddress`).

### Azure setup

See [SETUP_GUIDE.md](SETUP_GUIDE.md) for the full Microsoft Entra ID walkthrough: app registration, application
permissions, admin consent, creating and uploading a certificate, verification and rotation.

Design notes for the certificate strategy are in [design_certificate.md](design_certificate.md).

### Endpoints

| Endpoint | Purpose |
|---|---|
| `/mailOAuth/generate` | Start interactive sign-in, or redirect an admin to the consent page in daemon mode |
| `/mailOAuth/callback` | OAuth / admin-consent redirect target |
| `/mailOAuth/refresh` | Force a token refresh |
| `/mailOAuth/revoke` | Revoke and clear the stored token |
| `/mailOAuth/tokenStatus` | Show the current token state |
| `/readerToken/callback` | Redirect target for reader configs |

In daemon mode with admin consent already granted, no endpoint needs to be visited: the first send or read
acquires a token automatically.

### Build

```bash
./gradlew assemble      # build the plugin JAR
./gradlew test          # run tests
```

Requires Java 11 for the published artifact (the plugin builds on JDK 17 as well).

### Changelog

- **6.0-JDK11-1.0-M11** — Certificate-based (client assertion) authentication for Graph send and read
  (`useCertAuth`), `GraphAuthStrategy` abstraction, setup guide. PVR-107197.
- **6.0-JDK11-1.0-M10** — Login prompt parameter when generating a token.
