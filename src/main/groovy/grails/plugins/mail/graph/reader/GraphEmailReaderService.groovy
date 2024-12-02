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
        log.debug("Reading messages from ${mailFolderId} folder for config ${graphConfig.configName}")
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

        return messages
    }


    /*
    Move a message to another folder within the specified user's mailbox.
    This creates a new copy of the message in the destination folder and removes the original message.
    If successful, this method returns 201 Created response code and a message resource in the response body.
    reference: https://docs.microsoft.com/en-us/graph/api/message-move?view=graph-rest-1.0&tabs=java
    */

    Message moveMessage(GraphConfig graphConfig, String messageId, String destinationFolderId) {
        log.debug("Moving message ${messageId} to ${destinationFolderId} for config ${graphConfig.configName}")
        destinationFolderId = destinationFolderId ?: "deleteditems" //default is to delete folder
        GraphServiceClient serviceClient = graphApiClient.getClientFor(graphConfig)
        MovePostRequestBody movePostRequestBody = new MovePostRequestBody()
        movePostRequestBody.setDestinationId(destinationFolderId);
        Message message = serviceClient.me().messages().byMessageId(messageId).move().post(movePostRequestBody);
        return message
    }

    /*
    Delete a message in the specified user's mailbox, or delete a relationship of the message.
    If successful, this method returns 204 No Content response code
    reference: https://docs.microsoft.com/en-us/graph/api/message-delete?view=graph-rest-1.0&tabs=java
    */

    void deleteMessageById(GraphConfig graphConfig, String messageId) {
        log.debug("Deleting message ${messageId} for config ${graphConfig.configName}")
        //update a specific message
        GraphServiceClient serviceClient = graphApiClient.getClientFor(graphConfig)
        serviceClient.me().messages().byMessageId(messageId).delete()
    }

    /*
    Return the attachment of the message
    reference: https://docs.microsoft.com/en-us/graph/api/message-list-attachments?view=graph-rest-1.0&tabs=java
    */

    AttachmentCollectionResponse getMessageAttachments(GraphConfig graphConfig, String messageId) {
        log.debug("Collecting message ${messageId} attachmnets for config ${graphConfig.configName}")
        GraphServiceClient serviceClient = graphApiClient.getClientFor(graphConfig)
        AttachmentCollectionResponse attachments = serviceClient.me()
                .messages().byMessageId(messageId)
                .attachments()
                .get();
        return attachments
    }

    /*
    Get the mail folder collection directly under the root folder of the signed-in user
    reference: https://docs.microsoft.com/en-us/graph/api/user-list-mailfolders?view=graph-rest-1.0&tabs=java
    */

    MailFolderCollectionResponse listMailFolders(GraphConfig graphConfig) {
        log.debug("Collecting mail folders for config ${graphConfig.configName}")
        GraphServiceClient serviceClient = graphApiClient.getClientFor(graphConfig)
        MailFolderCollectionResponse mailFolders = serviceClient.me()
                .mailFolders()
                .get()
        return mailFolders
    }

    /*
    To create a new mail folder in the root folder of the user's mailbox.
    reference: https://docs.microsoft.com/en-us/graph/api/user-post-mailfolders?view=graph-rest-1.0&tabs=java
    */

    MailFolder createMailFolder(GraphConfig graphConfig, String mailFolderName) {
        log.debug("Creating mail folder ${mailFolderName} for config ${graphConfig.configName}")
        MailFolder mailFolder = new MailFolder(displayName: mailFolderName, isHidden: false)
        GraphServiceClient serviceClient = graphApiClient.getClientFor(graphConfig)
        return serviceClient.me()
                .mailFolders()
                .post(mailFolder)
    }

    void testConnection(GraphConfig graphConfig) throws ClientException {
        if (Holders.config.getProperty('grails.mail.reader.health.check.disabled', Boolean)) {
            log.warn("Health Check is disabled by config so no checking for $graphConfig.configName")
            return
        }
        readerTokenStoreService.refreshTokenFor(graphConfig)
    }

}
