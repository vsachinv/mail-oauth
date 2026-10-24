# Setup Guide — Microsoft Entra ID (Azure AD) for mail-oauth

This guide walks through registering an application in Microsoft Entra ID and configuring the `mail-oauth`
plugin for **daemon (unattended) mail send and read** using either a **client secret** or a **certificate**.

Interactive (delegated, user-login) mode is covered briefly at the end.

---

## 1. Decide the mode

| Mode | Who authenticates | Azure permission type | Credential | Plugin flags |
|---|---|---|---|---|
| Daemon + secret | The app itself | Application | Client secret | `daemon: true` |
| Daemon + certificate | The app itself | Application | PFX certificate | `daemon: true`, `useCertAuth: true` |
| Interactive | A signed-in user | Delegated | Client secret | `daemon: false` |

Daemon mode is recommended for servers. Certificate auth is preferred over a secret in production because the
private key never leaves the server and can be rotated without changing the plugin configuration.

---

## 2. Register the application

1. Sign in to the [Azure portal](https://portal.azure.com) and open **Microsoft Entra ID → App registrations → New registration**.
2. Give it a name (for example `mail-oauth-sender`).
3. **Supported account types**: *Accounts in this organizational directory only (single tenant)*.
4. **Redirect URI**: platform *Web*, value `https://<your-app-host>/mailOAuth/callback`.
   If you also use the reader, add `https://<your-app-host>/readerToken/callback`.
   These are needed for the admin-consent redirect and for interactive mode.
5. Click **Register**.
6. From the **Overview** page note down:
   - **Application (client) ID** → `client_id`
   - **Directory (tenant) ID** → `tenant_id`

---

## 3. Grant API permissions

1. Open **API permissions → Add a permission → Microsoft Graph**.
2. Choose **Application permissions** (for daemon mode).
3. Add what you need:

   | Feature | Permission |
   |---|---|
   | Send mail via Graph | `Mail.Send` |
   | Read / move / delete mail via Graph reader | `Mail.ReadWrite` |
   | Read mail via IMAP with OAuth | `IMAP.AccessAsApp` (plus an Exchange service principal, see Microsoft docs) |

4. Click **Grant admin consent for <tenant>** and confirm. The status column must show a green check.

> Application permissions always require tenant-admin consent. Without it every token request is rejected.

### Optional: restrict which mailboxes the app can touch

`Mail.Send` / `Mail.ReadWrite` as application permissions allow access to **every** mailbox in the tenant.
Limit this with an Exchange Online **Application Access Policy**:

```powershell
New-ApplicationAccessPolicy -AppId <client_id> -PolicyScopeGroupId <mail-enabled-security-group> `
    -AccessRight RestrictAccess -Description "mail-oauth sender"
Test-ApplicationAccessPolicy -Identity mailbox@company.com -AppId <client_id>
```

---

## 4a. Credential option A — client secret

1. Open **Certificates & secrets → Client secrets → New client secret**.
2. Set a description and expiry, click **Add**.
3. Copy the **Value** immediately; it is shown only once. This is `secret_val`.
4. Store it in a secrets manager or environment variable. Never commit it to source control.

Skip to step 5.

## 4b. Credential option B — certificate (PFX)

The plugin signs a JWT client assertion with the private key in a PKCS#12 (`.pfx` / `.p12`) keystore and
Azure verifies it against the public certificate you upload.

### Create a key pair

Using the JDK `keytool` (self-signed, 2 years):

```bash
keytool -genkeypair \
  -alias mailcert \
  -keyalg RSA -keysize 2048 \
  -sigalg SHA256withRSA \
  -validity 730 \
  -dname "CN=mail-oauth, O=YourCompany" \
  -storetype PKCS12 \
  -keystore mail-oauth.pfx \
  -storepass '<pfx-password>'

# export the public certificate for Azure
keytool -exportcert -alias mailcert -keystore mail-oauth.pfx -storepass '<pfx-password>' \
  -rfc -file mail-oauth.cer
```

Or with OpenSSL:

```bash
openssl req -x509 -newkey rsa:2048 -sha256 -days 730 -nodes \
  -keyout mail-oauth.key -out mail-oauth.cer -subj "/CN=mail-oauth/O=YourCompany"
openssl pkcs12 -export -name mailcert \
  -inkey mail-oauth.key -in mail-oauth.cer \
  -out mail-oauth.pfx -passout 'pass:<pfx-password>'
```

A certificate issued by your internal CA works the same way; only the public part is uploaded to Azure.

### Upload the public certificate

1. Open **Certificates & secrets → Certificates → Upload certificate**.
2. Select `mail-oauth.cer` (`.cer`, `.pem` or `.crt`), add a description, click **Add**.
3. Note the **Thumbprint** shown; it must match `keytool -list -v` on your PFX.

### Deploy the PFX

- Copy `mail-oauth.pfx` to the server, for example `/etc/app/mail-oauth.pfx`.
- Restrict permissions to the application user (`chmod 600`).
- The PFX password goes into a secrets manager or environment variable, not source control.
- Keep the alias (`mailcert` above) handy for `certificateAlias`. If the keystore has a single entry the alias is optional.

---

## 5. Configure the plugin

All properties live under `grails.mail` in `application.yml`.

### Sender — daemon + certificate

```yaml
grails:
  mail:
    username: mailbox@company.com               # mailbox to send as (mandatory in daemon mode)
    oAuth:
      enabled: true
      daemon: true
      useCertAuth: true
      client_id: ${MAIL_OAUTH_CLIENT_ID}
      tenant_id: ${MAIL_OAUTH_TENANT_ID}
      api_scope: https://graph.microsoft.com/.default
      callback_url: https://app.company.com/mailOAuth/callback
      certificatePath: /etc/app/mail-oauth.pfx
      certificatePassword: ${MAIL_OAUTH_PFX_PASSWORD}
      certificateAlias: mailcert                 # optional
      graph:
        enabled: true
```

### Sender — daemon + secret

```yaml
grails:
  mail:
    username: mailbox@company.com
    oAuth:
      enabled: true
      daemon: true
      client_id: ${MAIL_OAUTH_CLIENT_ID}
      tenant_id: ${MAIL_OAUTH_TENANT_ID}
      secret_val: ${MAIL_OAUTH_CLIENT_SECRET}
      api_scope: https://graph.microsoft.com/.default
      callback_url: https://app.company.com/mailOAuth/callback
      graph:
        enabled: true
```

### Reader — `GraphConfig` built by the consuming application

```groovy
GraphConfig config = new GraphConfig(
    configName   : 'inbox-reader',           // unique per mailbox / purpose
    clientId     : clientId,
    tenantId     : tenantId,
    scopes       : 'https://graph.microsoft.com/.default',
    emailAddress : 'shared@company.com',     // mandatory in daemon mode
    daemon       : true,
    // certificate
    useCertAuth        : true,
    certificatePath    : '/etc/app/mail-oauth.pfx',
    certificatePassword: pfxPassword,
    certificateAlias   : 'mailcert',
    // or secret instead of the four lines above
    // secretId: clientSecret,
)
```

### Multi-tenant deployments (Grails 7 line)

In the multi-tenant build every tenant has its own Entra app registration and credential. Repeat steps 2–4 per
tenant and put the credential keys under that tenant's block. `daemon` is read per tenant from the tenant's
own `oAuth.daemon` (unset or empty means `false`; the global `grails.mail.oAuth.daemon` applies only to the default
tenant), so tenants can independently choose app-only or interactive auth:

```groovy
grails.mail.oAuth.daemon = false                      // default tenant only

tenant_42.grails.mail.oAuth.daemon             = true
tenant_42.grails.mail.oAuth.client_id          = '...'
tenant_42.grails.mail.oAuth.tenant_id          = '<tenant42-entra-directory-id>'
tenant_42.grails.mail.oAuth.useCertAuth        = true
tenant_42.grails.mail.oAuth.certificatePath    = '/etc/app/tenant42.pfx'
tenant_42.grails.mail.oAuth.certificatePassword = '...'
```

Tenants may mix interactive and daemon flows and secret and certificate credentials. Admin consent (step 6) is per Entra tenant.

### Validation performed at startup

- `daemon: true` without `grails.mail.username` → startup fails.
- `useCertAuth: true` without `daemon: true` → startup fails (certificate auth is always app-only).
- Missing or unreadable `certificatePath` → first token request fails with a clear `IllegalStateException`.

---

## 6. Admin consent

Application permissions need tenant-admin consent **once per tenant**. Any of these work:

| Method | How |
|---|---|
| Azure portal | **API permissions → Grant admin consent for <tenant>** (done in step 3) |
| Direct URL | `https://login.microsoftonline.com/<tenant_id>/adminconsent?client_id=<client_id>` opened by an admin |
| Azure CLI | `az ad app permission admin-consent --id <client_id>` |
| Via the plugin | Admin opens `https://app.company.com/mailOAuth/generate` (or `/readerToken/generate`). The plugin redirects to the consent page and, on return, immediately fetches a token so you can verify the setup. |

If consent is already granted, **nothing needs to be triggered**. The first mail send (or read) finds an empty
token store, acquires a token with the configured credential, and caches it until expiry.

---

## 7. Verify

1. Start the application. The log prints `Enabled mail send graph configuration` (or the SMTP variant).
2. Send a test mail from the app, or open `/mailOAuth/tokenStatus` after visiting `/mailOAuth/generate` as an admin.
3. A successful certificate acquisition logs `[GRAPH_AUTH] [CERTIFICATE] Acquiring token via certificate client assertion`.

Common errors:

| Error | Cause |
|---|---|
| `AADSTS700027: Client assertion contains an invalid signature` | Uploaded `.cer` does not match the private key in the PFX, or wrong alias selected |
| `AADSTS7000215: Invalid client secret` | Wrong or expired `secret_val` |
| `AADSTS65001` / `Insufficient privileges` | Admin consent missing, or Delegated instead of Application permission |
| `ErrorAccessDenied` from Graph on send | Mailbox blocked by an Application Access Policy, or `username` is not a real mailbox |
| `certificatePath is not set or file does not exist` | Path wrong or file not readable by the app user |
| `No private key/certificate found ... for alias` | `certificateAlias` does not match an entry in the PFX |

---

## 8. Rotation

**Certificate**

1. Create a new PFX and upload its `.cer` to Azure alongside the old one.
2. Replace the file at `certificatePath` (and the password if it changed). The plugin reads the keystore on every
   token acquisition, so no restart is needed.
3. After the old token lifetime (about 1 hour) remove the old certificate from Azure.

**Secret**

1. Create a new secret in Azure, update `secret_val`, restart (or rely on the plugin's config-change handling).
2. Delete the old secret.

Azure limits secrets to 24 months; certificates can be longer-lived but should follow your PKI policy.

---

## 9. Interactive (delegated) mode — brief

Use this when mail must be sent as a specific signed-in user rather than by the app.

- Permission type **Delegated** (`Mail.Send`, `offline_access`, `User.Read`).
- `daemon: false`, `useCertAuth` must stay `false`, `secret_val` required.
- `api_scope`: `https://graph.microsoft.com/Mail.Send offline_access User.Read` (space separated).
- A user visits `/mailOAuth/generate`, signs in, and the plugin stores the access and refresh tokens.
  The token is validated to belong to `grails.mail.username`.
- Refresh happens automatically with the refresh token; if it is revoked the user must sign in again.
