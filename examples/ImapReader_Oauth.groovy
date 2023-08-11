import grails.plugins.mail.graph.GraphConfig
import grails.plugins.mail.graph.token.ReaderTokenStoreService
import grails.plugins.mail.imap.ImapConfig
import grails.plugins.mail.imap.reader.ImapEmailReaderService

import javax.mail.Folder
import javax.mail.Store;

ImapConfig imapConfig = new ImapConfig(configName: 'imap_sachin')

ImapEmailReaderService imapEmailReaderService = ctx.imapEmailReaderService

GraphConfig graphConfigObj = new GraphConfig(configName: 'imap_sachin_oauth2')

graphConfigObj.with {
    tenantId = 'common'
    clientId = '4520b008-7f88-4de4-bb14-5e55d0c5a1f9'
    secretId = 'DHo8Q~y9hkh7W7MqdlhBuFT1kZd7DCj0pvFgSawV'
    scopes = 'https://outlook.office.com/IMAP.AccessAsUser.All offline_access openid'
    callbackUrl = 'http://localhost:9090/reports/readerToken/callback'
}

imapConfig.with {
    username = 'v.sachin.v@gmail.com'
    password = ''
    port = 993
    host = 'outlook.office365.com'
    protocol = 'imaps'
    oAuthEnabled = true
    otherProperties = [
            "mail.imaps.starttls.enable"  : "true",
            "mail.imaps.connectiontimeout": "240000",
            "mail.imaps.timeout"          : "240000",
            "mail.imaps.writetimeout"     : "240000",
            "mail.imaps.partialfetch"     : "false",
            "mail.imaps.fetchsize"        : "1000000",

            "mail.imap.ssl.enable" :"true",
            "mail.imaps.sasl.enable" : "true",
            "mail.imaps.sasl.mechanisms": "XOAUTH2",
            "mail.imap.auth.login.disable": "true",
            "mail.imap.auth.plain.disable": "true"
    ]

    graphConfig = graphConfigObj

}

Store storeObj = imapEmailReaderService.getSessionStore(imapConfig)

imapEmailReaderService.createConnection(storeObj, imapConfig)

Folder folder = imapEmailReaderService.getFolder(storeObj,'Drafts')

println '----------'
println folder.messageCount
println '----------'
try {
    folder?.isOpen() ? folder.close(true) : ''
    storeObj?.isConnected() ? storeObj?.close() : ''
} catch (Exception ex) {
    ex.printStackTrace(System.out)
}
