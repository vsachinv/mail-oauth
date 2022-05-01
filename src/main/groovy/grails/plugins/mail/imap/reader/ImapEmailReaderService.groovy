package grails.plugins.mail.imap.reader

import grails.plugins.mail.graph.token.ReaderTokenStoreService
import grails.plugins.mail.imap.ImapConfig
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import javax.mail.Folder
import javax.mail.Session
import javax.mail.Store

@CompileStatic
@Slf4j
class ImapEmailReaderService {

    ReaderTokenStoreService readerTokenStoreService

    Store getSessionStore(ImapConfig imapConfig) {
        Properties properties = new Properties()
        properties.put('mail.store.protocol', imapConfig.protocol)
        imapConfig.otherProperties?.each {
            properties.put(it.key, it.value)
        }
        log.debug("Setting up ${properties.toString()} for imap connection ${imapConfig.configName}")
        Session.getInstance(properties).getStore(imapConfig.protocol)
    }

    void createConnection(Store store, ImapConfig imapConfig) {
        store.connect(imapConfig.host, imapConfig.port, imapConfig.username, getPassword(imapConfig))
    }

    private String getPassword(ImapConfig imapConfig) {
        if (imapConfig.oAuthEnabled) {
            log.debug("Returning OAuth2 access token for ${imapConfig.configName}")
            return readerTokenStoreService.getTokenFor(imapConfig.graphConfig).accessToken
        }
        return imapConfig.password
    }

    Folder getFolder(Store store, String folderName) {
        return store.getFolder(folderName)
    }

    Folder createFolder(Store store, String folderName) {
        Folder folder = store.defaultFolder.getFolder(folderName)
        if (!folder.exists()) {
            log.debug("Creating new mail folder as $folderName using Imap protocol")
            folder.create(Folder.HOLDS_MESSAGES);
        }
        return folder
    }

    void closeConnections(Folder folder, Store store) {
        try {
            folder?.isOpen() ? folder.close(true) : ''
            store?.isConnected() ? store?.close() : ''
        } catch (Exception ex) {
            log.warn("Exception while closing imap connections error: ${ex.message}")
        }
    }

}
