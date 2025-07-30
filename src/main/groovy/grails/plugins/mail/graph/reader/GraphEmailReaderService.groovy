package grails.plugins.mail.graph.reader

import com.microsoft.graph.core.exceptions.ClientException
import com.microsoft.graph.models.AttachmentCollectionResponse
import com.microsoft.graph.models.MailFolder
import com.microsoft.graph.models.MailFolderCollectionResponse
import com.microsoft.graph.models.Message
import com.microsoft.graph.models.MessageCollectionResponse
import com.microsoft.graph.serviceclient.GraphServiceClient
import com.microsoft.graph.users.item.messages.item.move.MovePostRequestBody
import com.microsoft.graph.users.item.mailfolders.item.messages.*
import grails.plugins.mail.graph.GraphApiClient
import grails.plugins.mail.graph.GraphConfig
import grails.plugins.mail.graph.token.ReaderTokenStoreService
import grails.util.Holders
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import java.util.function.Consumer

@Slf4j
@CompileStatic
class GraphEmailReaderService {

    GraphApiClient graphApiClient
    ReaderTokenStoreService readerTokenStoreService

    //provide graph config with access token, expire time and api scope, make sure token is not not expired, if expired, regenerate

    /*
    reference: https://docs.microsoft.com/en-us/graph/api/user-list-messages?view=graph-rest-1.0&tabs=java
    return top 10 message of the inbox folder from mailbox, use $select and @odata.nextLink for further selection from the data
    */

    MessageCollectionResponse listMessages(GraphConfig graphConfig, String mailFolderId, int topMaxMessage) {
        log.info("[GRAPH_EMAIL] [LIST_MESSAGES] [STARTED] - EMAIL_ADDRESS=${graphConfig.emailAddress} | FOLDER=${mailFolderId ?: 'Inbox'}")
        mailFolderId = mailFolderId ?: 'Inbox'
        topMaxMessage = topMaxMessage ?: 10
        GraphServiceClient serviceClient = graphApiClient.getClientFor(graphConfig)

        MessageCollectionResponse messages = serviceClient
                .me()
                .mailFolders()
                .byMailFolderId(mailFolderId)
                .messages()
                .get(new Consumer<MessagesRequestBuilder.GetRequestConfiguration>() {
                    @Override
                    void accept(MessagesRequestBuilder.GetRequestConfiguration requestConfiguration) {
                        requestConfiguration.queryParameters.top = topMaxMessage
                    }
                })

        log.info("[GRAPH_EMAIL] [LIST_MESSAGES] [SUCCESS] - EMAIL_ADDRESS=${graphConfig.emailAddress} | COUNT=${messages?.value?.size()}")
        return messages
    }


    /*
    Move a message to another folder within the specified user's mailbox.
    This creates a new copy of the message in the destination folder and removes the original message.
    If successful, this method returns 201 Created response code and a message resource in the response body.
    reference: https://docs.microsoft.com/en-us/graph/api/message-move?view=graph-rest-1.0&tabs=java
    */

    Message moveMessage(GraphConfig graphConfig, String messageId, String destinationFolderId) {
        log.info("[GRAPH_EMAIL] [MOVE_MESSAGE] [STARTED] - EMAIL_ADDRESS=${graphConfig.emailAddress} | MESSAGE_ID=${messageId} | DEST_FOLDER=${destinationFolderId ?: 'deleteditems'}")
        destinationFolderId = destinationFolderId ?: "deleteditems" //default is to delete folder
        GraphServiceClient serviceClient = graphApiClient.getClientFor(graphConfig)
        MovePostRequestBody movePostRequestBody = new MovePostRequestBody()
        movePostRequestBody.setDestinationId(destinationFolderId);
        Message message = serviceClient.me().messages().byMessageId(messageId).move().post(movePostRequestBody);
        log.info("[GRAPH_EMAIL] [MOVE_MESSAGE] [SUCCESS] - EMAIL_ADDRESS=${graphConfig.emailAddress} | NEW_FOLDER=${destinationFolderId}")
        return message
    }

    /*
    Delete a message in the specified user's mailbox, or delete a relationship of the message.
    If successful, this method returns 204 No Content response code
    reference: https://docs.microsoft.com/en-us/graph/api/message-delete?view=graph-rest-1.0&tabs=java
    */

    void deleteMessageById(GraphConfig graphConfig, String messageId) {
        log.info("[GRAPH_EMAIL] [DELETE_MESSAGE] [STARTED] - EMAIL_ADDRESS=${graphConfig.emailAddress} | MESSAGE_ID=${messageId}")

        GraphServiceClient serviceClient = graphApiClient.getClientFor(graphConfig)
        serviceClient.me().messages().byMessageId(messageId).delete()
        log.info("[GRAPH_EMAIL] [DELETE_MESSAGE] [SUCCESS] - EMAIL_ADDRESS=${graphConfig.emailAddress} | MESSAGE_ID=${messageId}")
    }

    /*
    Return the attachment of the message
    reference: https://docs.microsoft.com/en-us/graph/api/message-list-attachments?view=graph-rest-1.0&tabs=java
    */

    AttachmentCollectionResponse getMessageAttachments(GraphConfig graphConfig, String messageId) {
        log.info("[GRAPH_EMAIL] [COLLECT_ATTACHMENTS] [STARTED] - EMAIL_ADDRESS=${graphConfig.emailAddress} | MESSAGE_ID=${messageId}")
        GraphServiceClient serviceClient = graphApiClient.getClientFor(graphConfig)
        AttachmentCollectionResponse attachments = serviceClient
                .me().messages().byMessageId(messageId)
                .attachments().get()

        log.info("[GRAPH_EMAIL] [COLLECT_ATTACHMENTS] [SUCCESS] - EMAIL_ADDRESS=${graphConfig.emailAddress} | COUNT=${attachments?.value?.size()}")
        return attachments

    }

    /*
    Get the mail folder collection directly under the root folder of the signed-in user
    reference: https://docs.microsoft.com/en-us/graph/api/user-list-mailfolders?view=graph-rest-1.0&tabs=java
    */

    MailFolderCollectionResponse listMailFolders(GraphConfig graphConfig) {
        log.info("[GRAPH_EMAIL] [COLLECT_MAIL_FOLDERS] [STARTED] - EMAIL_ADDRESS=${graphConfig.emailAddress}")

        GraphServiceClient serviceClient = graphApiClient.getClientFor(graphConfig)
        MailFolderCollectionResponse folders = serviceClient.me().mailFolders().get()
        log.info("[GRAPH_EMAIL] [COLLECT_MAIL_FOLDERS] [SUCCESS] - EMAIL_ADDRESS=${graphConfig.emailAddress} | COUNT=${folders?.value?.size()}")
        return folders
    }

    /*
    To create a new mail folder in the root folder of the user's mailbox.
    reference: https://docs.microsoft.com/en-us/graph/api/user-post-mailfolders?view=graph-rest-1.0&tabs=java
    */

    MailFolder createMailFolder(GraphConfig graphConfig, String mailFolderName) {
        log.info("[GRAPH_EMAIL] [CREATE_MAIL_FOLDER] [STARTED] - EMAIL_ADDRESS=${graphConfig.emailAddress} | FOLDER_NAME=${mailFolderName}")

        MailFolder mailFolder = new MailFolder(displayName: mailFolderName, isHidden: false)
        GraphServiceClient serviceClient = graphApiClient.getClientFor(graphConfig)
        MailFolder created = serviceClient.me().mailFolders().post(mailFolder)
        log.info("[GRAPH_EMAIL] [CREATE_MAIL_FOLDER] [SUCCESS] - EMAIL_ADDRESS=${graphConfig.emailAddress} | FOLDER_NAME=${mailFolderName}")
        return created
    }

    void testConnection(GraphConfig graphConfig) throws ClientException {
        if (Holders.config.getProperty('grails.mail.reader.health.check.disabled', Boolean)) {
            log.warn("[GRAPH_EMAIL] [HEALTH_CHECK] Disabled via config so no checking for EMAIL_ADDRESS=${graphConfig.emailAddress}")
            return
        }

        try {
            log.info("[GRAPH_EMAIL] [HEALTH_CHECK] [STARTED] - EMAIL_ADDRESS=${graphConfig.emailAddress}")
            readerTokenStoreService.refreshTokenFor(graphConfig)
            log.info("[GRAPH_EMAIL] [HEALTH_CHECK] [SUCCESS] - EMAIL_ADDRESS=${graphConfig.emailAddress}")
        } catch (Exception ex) {
            log.error("[GRAPH_EMAIL] [HEALTH_CHECK] [FAILED] - EMAIL_ADDRESS=${graphConfig.emailAddress} | ERROR=${ex.message}", ex)
            throw ex
        }
    }
}
