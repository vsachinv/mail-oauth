package grails.plugins.mail.graph.reader

import com.microsoft.graph.core.exceptions.ClientException
import com.microsoft.graph.models.AttachmentCollectionResponse
import com.microsoft.graph.models.MailFolder
import com.microsoft.graph.models.MailFolderCollectionResponse
import com.microsoft.graph.models.Message
import com.microsoft.graph.models.MessageCollectionResponse
import com.microsoft.graph.serviceclient.GraphServiceClient
import com.microsoft.graph.users.item.UserItemRequestBuilder
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
        mailFolderId = mailFolderId ?: 'Inbox'
        topMaxMessage = topMaxMessage ?: 10
        log.debug("[GRAPH_READ_EMAIL] [LIST_MESSAGES] [STARTED] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | FOLDER=${mailFolderId}")
        MessageCollectionResponse messages = getUserItemRequestBuilder(graphConfig)
                .mailFolders()
                .byMailFolderId(mailFolderId)
                .messages()
                .get(new Consumer<MessagesRequestBuilder.GetRequestConfiguration>() {
                    @Override
                    void accept(MessagesRequestBuilder.GetRequestConfiguration requestConfiguration) {
                        requestConfiguration.queryParameters.top = topMaxMessage
                    }
                })

        log.debug("[GRAPH_READ_EMAIL] [LIST_MESSAGES] [SUCCESS] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | FOLDER=${mailFolderId} | COUNT=${messages?.value?.size()}")
        return messages
    }


    /*
    Move a message to another folder within the specified user's mailbox.
    This creates a new copy of the message in the destination folder and removes the original message.
    If successful, this method returns 201 Created response code and a message resource in the response body.
    reference: https://docs.microsoft.com/en-us/graph/api/message-move?view=graph-rest-1.0&tabs=java
    */

    Message moveMessage(GraphConfig graphConfig, String messageId, String destinationFolderId) {
        destinationFolderId = destinationFolderId ?: "deleteditems" //default is to delete folder
        log.debug("[GRAPH_READ_EMAIL] [MOVE_MESSAGE] [STARTED] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | MESSAGE_ID=${messageId} | DEST_FOLDER=${destinationFolderId}")
        MovePostRequestBody movePostRequestBody = new MovePostRequestBody()
        movePostRequestBody.setDestinationId(destinationFolderId);
        Message message = getUserItemRequestBuilder(graphConfig).messages().byMessageId(messageId).move().post(movePostRequestBody);
        log.debug("[GRAPH_READ_EMAIL] [MOVE_MESSAGE] [SUCCESS] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | NEW_FOLDER=${destinationFolderId} for config ${graphConfig.configName}")
        return message
    }

    /*
    Delete a message in the specified user's mailbox, or delete a relationship of the message.
    If successful, this method returns 204 No Content response code
    reference: https://docs.microsoft.com/en-us/graph/api/message-delete?view=graph-rest-1.0&tabs=java
    */

    void deleteMessageById(GraphConfig graphConfig, String messageId) {
        log.debug("[GRAPH_READ_EMAIL] [DELETE_MESSAGE] [STARTED] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | MESSAGE_ID=${messageId}")
        //update a specific message
        getUserItemRequestBuilder(graphConfig).messages().byMessageId(messageId).delete()
        log.debug("[GRAPH_READ_EMAIL] [DELETE_MESSAGE] [SUCCESS] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | MESSAGE_ID=${messageId}")
    }

    /*
    Return the attachment of the message
    reference: https://docs.microsoft.com/en-us/graph/api/message-list-attachments?view=graph-rest-1.0&tabs=java
    */

    AttachmentCollectionResponse getMessageAttachments(GraphConfig graphConfig, String messageId) {
        log.debug("[GRAPH_READ_EMAIL] [COLLECT_ATTACHMENTS] [STARTED] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | MESSAGE_ID=${messageId}")
        AttachmentCollectionResponse attachments = getUserItemRequestBuilder(graphConfig)
                .messages().byMessageId(messageId)
                .attachments()
                .get();
        log.debug("[GRAPH_READ_EMAIL] [COLLECT_ATTACHMENTS] [SUCCESS] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | COUNT=${attachments?.value?.size()}")
        return attachments
    }

    /*
    Get the mail folder collection directly under the root folder of the signed-in user
    reference: https://docs.microsoft.com/en-us/graph/api/user-list-mailfolders?view=graph-rest-1.0&tabs=java
    */

    MailFolderCollectionResponse listMailFolders(GraphConfig graphConfig) {
        log.debug("[GRAPH_READ_EMAIL] [LIST_MAIL_FOLDERS] [STARTED] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress}")
        MailFolderCollectionResponse mailFolders = getUserItemRequestBuilder(graphConfig)
                .mailFolders()
                .get()
        log.debug("[GRAPH_READ_EMAIL] [LIST_MAIL_FOLDERS] [SUCCESS] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | COUNT=${mailFolders?.value?.size()}")
        return mailFolders
    }

    /*
    To create a new mail folder in the root folder of the user's mailbox.
    reference: https://docs.microsoft.com/en-us/graph/api/user-post-mailfolders?view=graph-rest-1.0&tabs=java
    */

    MailFolder createMailFolder(GraphConfig graphConfig, String mailFolderName) {
        log.debug("[GRAPH_READ_EMAIL] [CREATE_MAIL_FOLDER] [STARTED] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | FOLDER_NAME=${mailFolderName}")
        MailFolder mailFolder = new MailFolder(displayName: mailFolderName, isHidden: false)
        MailFolder created = getUserItemRequestBuilder(graphConfig).mailFolders().post(mailFolder)
        log.info("[GRAPH_READ_EMAIL] [CREATE_MAIL_FOLDER] [SUCCESS] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | FOLDER_NAME=${mailFolderName}")
        return created
    }

    void testConnection(GraphConfig graphConfig) throws ClientException {
        if (Holders.config.getProperty('grails.mail.reader.health.check.disabled', Boolean)) {
            log.warn("[GRAPH_READ_EMAIL] [HEALTH_CHECK] Disabled via config so no checking for - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress}")
            return
        }
        readerTokenStoreService.refreshTokenFor(graphConfig)
    }

    private UserItemRequestBuilder getUserItemRequestBuilder(GraphConfig graphConfig) {
        GraphServiceClient serviceClient = graphApiClient.getClientFor(graphConfig)
        UserItemRequestBuilder userRequestBuilder = serviceClient.me()
        if (graphConfig.daemon || (graphConfig.emailAddress && graphConfig.isShared)) {
            log.debug("[GRAPH_READ_EMAIL] [GET_USER_ITEM_REQUEST_BUILDER] taking as ${graphConfig.emailAddress}")
            userRequestBuilder = serviceClient.users().byUserId(graphConfig.emailAddress)
        }
        return userRequestBuilder
    }

}
