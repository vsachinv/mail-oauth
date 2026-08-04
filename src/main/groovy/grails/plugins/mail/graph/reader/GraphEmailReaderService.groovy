package grails.plugins.mail.graph.reader

import com.microsoft.graph.core.exceptions.ClientException
import com.microsoft.graph.models.Attachment
import com.microsoft.graph.models.AttachmentCollectionResponse
import com.microsoft.graph.models.MailFolder
import com.microsoft.graph.models.MailFolderCollectionResponse
import com.microsoft.graph.models.Message
import com.microsoft.graph.models.MessageCollectionResponse
import com.microsoft.graph.serviceclient.GraphServiceClient
import com.microsoft.graph.users.item.UserItemRequestBuilder
import com.microsoft.graph.users.item.messages.item.attachments.AttachmentsRequestBuilder
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

    /*
    $select set that lists attachments without their content. Prefer it over a hand-rolled list:
    dropping 'isInline' hides inline signature images among real attachments, dropping 'contentType'
    breaks type based routing.
    */
    static final List<String> ATTACHMENT_METADATA_FIELDS =
            ['id', 'name', 'contentType', 'size', 'isInline', 'lastModifiedDateTime'].asImmutable()

    /* Graph 400s on '@odata.type' as a $select field but returns it either way, so it is stripped. */
    private static final List<String> DISALLOWED_SELECT_FIELDS = ['@odata.type', 'odata.type'].asImmutable()

    /* Handle passed to getMessageAttachment, so it is never allowed to be selected away. */
    private static final String MANDATORY_SELECT_FIELD = 'id'

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
    All attachments of the message, content included: fileAttachment.contentBytes arrives inline as
    base64, about 1.33x the file size, for every attachment. Prefer the 3 arg overload where
    attachments can be large.
    Throws IllegalStateException if Graph returns an attachment without an @odata.type.
    reference: https://docs.microsoft.com/en-us/graph/api/message-list-attachments?view=graph-rest-1.0&tabs=java
    */

    AttachmentCollectionResponse getMessageAttachments(GraphConfig graphConfig, String messageId) {
        return fetchMessageAttachments(graphConfig, messageId, null)
    }

    /*
    Attachments of the message restricted to the given OData $select fields, which keeps
    contentBytes out of the response. Pass ATTACHMENT_METADATA_FIELDS unless a narrower set is
    needed.

    selectFields is sanitised first: blanks and '@odata.type' dropped, duplicates removed, 'id'
    forced in. A null, empty or fully stripped list warns and falls back to the content bearing
    fetch above.
    Throws IllegalStateException if Graph returns an attachment without an @odata.type.
    reference: https://docs.microsoft.com/en-us/graph/api/message-list-attachments?view=graph-rest-1.0
    */

    AttachmentCollectionResponse getMessageAttachments(GraphConfig graphConfig, String messageId, List<String> selectFields) {
        List<String> effectiveFields = sanitiseSelectFields(selectFields)
        if (!effectiveFields) {
            log.warn("[GRAPH_READ_EMAIL] [COLLECT_ATTACHMENTS] [NO_USABLE_SELECT] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | MESSAGE_ID=${messageId} | REQUESTED_SELECT=${selectFields} - falling back to a full fetch that returns fileAttachment.contentBytes inline for every attachment, pass ATTACHMENT_METADATA_FIELDS to avoid this")
        }
        return fetchMessageAttachments(graphConfig, messageId, effectiveFields)
    }

    /*
    One attachment by id, content included. Pairs with the $select overload above: list metadata
    first, then fetch content only for the attachments actually needed, one call each.

    Returns a FileAttachment, ItemAttachment or ReferenceAttachment, so branch on the subtype:
    ItemAttachment content is not on this endpoint at all - an attached .msg/.eml needs
    $expand=microsoft.graph.itemAttachment/item - and absent content there is not an empty
    attachment. The v6 SDK generates no '$value' builder for message attachments, so contentBytes is
    held in memory once decoded; do not fan this call out over every attachment of a large message.

    Throws IllegalArgumentException on a blank messageId or attachmentId, IllegalStateException if
    Graph returns no @odata.type.
    reference: https://docs.microsoft.com/en-us/graph/api/attachment-get?view=graph-rest-1.0&tabs=java
    */

    Attachment getMessageAttachment(GraphConfig graphConfig, String messageId, String attachmentId) {
        if (!messageId || !attachmentId) {
            throw new IllegalArgumentException("messageId and attachmentId are both required to fetch an attachment, got messageId=${messageId} attachmentId=${attachmentId}")
        }
        log.debug("[GRAPH_READ_EMAIL] [COLLECT_ATTACHMENT] [STARTED] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | MESSAGE_ID=${messageId} | ATTACHMENT_ID=${attachmentId}")
        Attachment attachment = getUserItemRequestBuilder(graphConfig)
                .messages().byMessageId(messageId)
                .attachments().byAttachmentId(attachmentId)
                .get()
        if (isUntyped(attachment)) {
            log.error("[GRAPH_READ_EMAIL] [COLLECT_ATTACHMENT] [UNTYPED_ATTACHMENT] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | MESSAGE_ID=${messageId} | ATTACHMENT_ID=${attachmentId}")
            throw new IllegalStateException("Graph returned attachment ${attachmentId} of message ${messageId} without an @odata.type discriminator, so its content cannot be read")
        }
        log.debug("[GRAPH_READ_EMAIL] [COLLECT_ATTACHMENT] [SUCCESS] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | MESSAGE_ID=${messageId} | ATTACHMENT_ID=${attachmentId} | TYPE=${attachment?.getClass()?.simpleName} | CONTENT_TYPE=${attachment?.contentType} | SIZE=${attachment?.size}")
        return attachment
    }

    /* Single request path behind both public overloads. A null or empty selectFields means no $select. */
    private AttachmentCollectionResponse fetchMessageAttachments(GraphConfig graphConfig, String messageId, List<String> selectFields) {
        log.debug("[GRAPH_READ_EMAIL] [COLLECT_ATTACHMENTS] [STARTED] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | MESSAGE_ID=${messageId} | SELECT=${selectFields ?: 'ALL'}")
        AttachmentsRequestBuilder attachmentsRequestBuilder = getUserItemRequestBuilder(graphConfig)
                .messages().byMessageId(messageId)
                .attachments()
        AttachmentCollectionResponse attachments = selectFields ?
                attachmentsRequestBuilder.get(new Consumer<AttachmentsRequestBuilder.GetRequestConfiguration>() {
                    @Override
                    void accept(AttachmentsRequestBuilder.GetRequestConfiguration requestConfiguration) {
                        requestConfiguration.queryParameters.select = selectFields as String[]
                    }
                }) : attachmentsRequestBuilder.get()
        verifyAttachmentCollection(graphConfig, messageId, selectFields, attachments)
        log.debug("[GRAPH_READ_EMAIL] [COLLECT_ATTACHMENTS] [SUCCESS] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | COUNT=${attachments?.value?.size()}")
        return attachments
    }

    /*
    The usable $select fields, or [] when nothing usable is left. Drops blanks ('$select=id,,name'
    is a 400) and DISALLOWED_SELECT_FIELDS, dedupes case insensitively, and prepends 'id' unless
    already present verbatim - so metadata never comes back without the handle needed to fetch its
    content. Verbatim because OData property names are case sensitive: a caller's 'ID' is a field
    Graph does not have and does not count as the id.
    */
    private static List<String> sanitiseSelectFields(List<String> selectFields) {
        List<String> cleaned = []
        selectFields?.each { String field ->
            String trimmed = field?.trim()
            boolean usable = trimmed && !DISALLOWED_SELECT_FIELDS.contains(trimmed.toLowerCase())
            if (usable && !cleaned.any { String kept -> kept.equalsIgnoreCase(trimmed) }) {
                cleaned.add(trimmed)
            }
        }
        if (!cleaned) {
            return []
        }
        if (!cleaned.contains(MANDATORY_SELECT_FIELD)) {
            cleaned.add(0, MANDATORY_SELECT_FIELD)
        }
        return cleaned
    }

    /*
    Two failures Graph signals only by omission.

    Attachment.createFromDiscriminatorValue returns the abstract base Attachment when '@odata.type'
    is missing or unrecognised - it does not raise. Callers branch on FileAttachment /
    ItemAttachment / ReferenceAttachment, so such an element matches none of them and the attachment
    is lost behind a 200 OK. Throws instead, so the message is not marked processed while its
    attachments are missing.

    An '@odata.nextLink' means value holds a partial set. This endpoint returns every attachment in
    one response today, so warn rather than page.
    */
    private void verifyAttachmentCollection(GraphConfig graphConfig, String messageId, List<String> selectFields,
                                            AttachmentCollectionResponse attachments) {
        if (attachments?.odataNextLink) {
            log.warn("[GRAPH_READ_EMAIL] [COLLECT_ATTACHMENTS] [PARTIAL_PAGE] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | MESSAGE_ID=${messageId} | COUNT=${attachments?.value?.size()} - @odata.nextLink is present so the returned collection is not the complete attachment set")
        }
        List<Attachment> attachmentList = attachments?.value
        if (!attachmentList) {
            return
        }
        List<Attachment> untyped = attachmentList.findAll { Attachment attachment -> isUntyped(attachment) }
        if (untyped) {
            log.error("[GRAPH_READ_EMAIL] [COLLECT_ATTACHMENTS] [UNTYPED_ATTACHMENT] - CONFIG=${graphConfig.configName} | EMAIL_ADDRESS=${graphConfig.emailAddress} | MESSAGE_ID=${messageId} | UNTYPED_COUNT=${untyped.size()} | TOTAL_COUNT=${attachmentList.size()} | SELECT=${selectFields ?: 'ALL'}")
            throw new IllegalStateException("Graph returned ${untyped.size()} of ${attachmentList.size()} attachment(s) of message ${messageId} without an @odata.type discriminator, refusing to process a partially typed attachment collection")
        }
    }

    /* True when the SDK could not resolve a concrete attachment subtype and handed back the base type. */
    private static boolean isUntyped(Attachment attachment) {
        return attachment != null && attachment.getClass() == Attachment
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
