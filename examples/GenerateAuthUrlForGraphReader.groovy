import grails.plugins.mail.graph.GraphConfig
import grails.plugins.mail.graph.token.ReaderTokenStoreService



GraphConfig graphConfigObj = new GraphConfig(configName: 'imap_sachin_oauth_graph')

graphConfigObj.with {
    tenantId = 'common'
    clientId = 'e05f6008-f67b-4d28-a2fb-4a66e1fde90f'
    secretId = 'O6l8Q~0uQbqWSLgWHwL.qu.5b-EYKb_atyOIGcjk'
    scopes = 'Files.ReadWrite.AppFolder Mail.ReadWrite offline_access openid'
    callbackUrl = 'http://localhost:9090/reports/readerToken/callback'
}

ReaderTokenStoreService readerTokenStoreService = ctx.readerTokenStoreService

println readerTokenStoreService.generateAuthCodeURLFor(graphConfigObj)