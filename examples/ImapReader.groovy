import grails.plugins.mail.imap.ImapConfig
import grails.plugins.mail.imap.reader.ImapEmailReaderService

import javax.mail.Folder
import javax.mail.Store;

ImapConfig imapConfig = new ImapConfig(configName: 'imap_sachin')

ImapEmailReaderService imapEmailReaderService = ctx.imapEmailReaderService

imapConfig.with {
    username = ''
    password = ''
    port = 993
    host = 'outlook.office365.com'
    protocol = 'imaps'
    oAuthEnabled = false
    otherProperties = [
            "mail.imaps.starttls.enable"  : "true",
            "mail.imaps.connectiontimeout": "240000",
            "mail.imaps.timeout"          : "240000",
            "mail.imaps.writetimeout"     : "240000",
            "mail.imaps.partialfetch"     : "false",
            "mail.imaps.fetchsize"        : "1000000"
    ]

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
