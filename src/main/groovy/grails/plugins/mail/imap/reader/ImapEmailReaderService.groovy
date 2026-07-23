package grails.plugins.mail.imap.reader

import grails.plugins.mail.graph.token.ReaderTokenStoreService
import grails.plugins.mail.imap.ImapConfig
import grails.plugins.mail.oauth.token.OAuthToken
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.Store

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
        log.debug("[GRAPH_EMAIL] [IMAP_READER] [SESSION_STORE] Setting up ${properties.toString()} for imap connection ${imapConfig.configName}")
        Session.getInstance(properties).getStore(imapConfig.protocol)
    }

    void createConnection(Store store, ImapConfig imapConfig) {
        store.connect(imapConfig.host, imapConfig.port, imapConfig.username, getPassword(imapConfig))
    }

    private String getPassword(ImapConfig imapConfig) {
        if (imapConfig.oAuthEnabled) {
            log.debug("[GRAPH_EMAIL] [IMAP_READER] [GET_PASSWORD] Returning OAuth2 access token for ${imapConfig.configName}")
            OAuthToken oAuthToken = readerTokenStoreService.getTokenFor(imapConfig.graphConfig)
            if (!oAuthToken) {
                throw new Exception("Valid OAuth Token not found for ${imapConfig.configName} in token repo. Please get the same generated first")
            }
            return oAuthToken.accessToken
        }
        return imapConfig.password
    }

    Folder getFolder(Store store, String folderName) {
        return store.getFolder(folderName)
    }

    Folder createFolder(Store store, String folderName) {
        Folder folder = store.defaultFolder.getFolder(folderName)
        if (!folder.exists()) {
            log.debug("[GRAPH_EMAIL] [IMAP_READER] [CREATE_MAIL_FOLDER] - FOLDER_NAME=${folderName}")
            folder.create(Folder.HOLDS_MESSAGES);
        }
        return folder
    }

    void closeConnections(Folder folder, Store store) {
        try {
            folder?.isOpen() ? folder.close(true) : ''
            store?.isConnected() ? store?.close() : ''
        } catch (Exception ex) {
            log.warn("[GRAPH_EMAIL] [IMAP_READER] [CLOSE_CONNECTION] Exception while closing imap connections error: ${ex.message}")
        }
    }

}
