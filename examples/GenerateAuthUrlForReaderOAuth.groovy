import grails.plugins.mail.graph.GraphConfig
import grails.plugins.mail.graph.token.ReaderTokenStoreService


GraphConfig graphConfigObj = new GraphConfig(configName: 'imap_sachin_oauth2')

graphConfigObj.with {
    tenantId = 'common'
    clientId = '4520b008-7f88-4de4-bb14-5e55d0c5a1f9'
    secretId = 'DHo8Q~y9hkh7W7MqdlhBuFT1kZd7DCj0pvFgSawV'
    scopes = 'https://outlook.office.com/IMAP.AccessAsUser.All offline_access openid'
    callbackUrl = 'http://localhost:9090/reports/readerToken/callback'
}

ReaderTokenStoreService readerTokenStoreService = ctx.readerTokenStoreService

println readerTokenStoreService.generateAuthCodeURLFor(graphConfigObj)