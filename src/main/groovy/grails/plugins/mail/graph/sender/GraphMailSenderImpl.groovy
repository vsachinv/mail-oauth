package grails.plugins.mail.graph.sender

import com.microsoft.graph.models.AttachmentItem
import com.microsoft.graph.models.AttachmentType
import com.microsoft.graph.models.FileAttachment
import com.microsoft.graph.models.UploadSession
import com.microsoft.graph.serviceclient.GraphServiceClient
import com.microsoft.graph.core.tasks.LargeFileUploadTask
import com.microsoft.graph.users.item.UserItemRequestBuilder
import com.microsoft.graph.users.item.messages.item.attachments.createuploadsession.CreateUploadSessionPostRequestBody
import com.microsoft.kiota.ApiException
import com.microsoft.kiota.serialization.ParsableFactory
import com.microsoft.kiota.serialization.ParseNode
import grails.plugins.mail.GrailsMailException
import grails.plugins.mail.graph.GraphApiClient
import grails.plugins.mail.oauth.sender.OAuthMailSenderImpl
import grails.util.Holders
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import com.microsoft.graph.models.Message
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailException
import org.springframework.mail.MailSendException
import javax.mail.AuthenticationFailedException
import javax.mail.internet.MimeMessage
import java.time.Clock
import java.time.OffsetDateTime

@Slf4j
@CompileStatic
class GraphMailSenderImpl extends OAuthMailSenderImpl {

    GraphApiClient graphApiClient
    int maxAttachmentSizeInMB = 3
    boolean daemon = false

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) throws MailException {
        log.warn("[GRAPH_EMAIL] [UNSUPPORTED_METHOD] doSend() called — This method is not supported, please use sendMailViaGraph.")
        throw new GrailsMailException("Please use sendMailViaGraph instead of doSend.")
    }

    void sendMailViaGraph(Message message, List<FileAttachment> attachmentList) throws MailException {
        Map<Object, Exception> failedMessages = new LinkedHashMap<Object, Exception>()
        boolean connectionStatus = !!mailOAuthService.accessToken
        try {
            if (!connectionStatus) {
                throw new MailAuthenticationException(new AuthenticationFailedException())
            }
        } catch (Exception ex) {
            failedMessages.put(message, ex)
            throw new MailSendException("Mail server connection failed", ex, failedMessages)
        }
        try {
            log.debug("[GRAPH_EMAIL] [SEND_EMAIL] [STARTED] - Recipients=${message?.toRecipients*.emailAddress*.address} - From=${message?.from?.emailAddress?.address} | Attachments=${attachmentList?.size()}")
            processAttachmentAndSendMsg(message, attachmentList)
            log.info("[GRAPH_EMAIL] [SEND_EMAIL] [SUCCESS] - Email sent to ${message?.toRecipients*.emailAddress*.address} - From=${message?.from?.emailAddress?.address}")
        } catch (ApiException ex) {
            log.error("[GRAPH_EMAIL] [SEND_EMAIL] [FAILED] - Graph API exception: ${ex.message}", ex)
            failedMessages.put(message, ex)
        } finally {
            if (!connectionStatus) {
                log.error("[GRAPH_EMAIL] [SEND_EMAIL] [FAILED] - Connection to Graph server failed. Check client ID, secret, or token validity.")
            }
        }
        if (!failedMessages.isEmpty()) {
            log.warn("[GRAPH_EMAIL] [SEND_EMAIL] [PARTIAL_FAILURE] - Some emails failed to send.")
            throw new MailSendException(failedMessages)
        }
    }

    private void processAttachmentAndSendMsg(Message message, List<FileAttachment> attachmentList) throws ApiException {
        log.debug("[GRAPH_EMAIL] [SEND_EMAIL] [PROCESS_ATTACHMENT_MSG] - Sending email to ${message.toRecipients ? (message?.toRecipients*.emailAddress*.address) : ''} with ${attachmentList?.size()} attachments using graph protocol")
        GraphServiceClient graphServiceClient = graphApiClient.standardMailClient
        message.attachments = []
        UserItemRequestBuilder userItemRequestBuilder = graphServiceClient.me()
        // DefaultFrom is mandatory in case of daemon. and if username is different then it means using shared account (Use default from config for shared).
        String fromAddress = message.from?.emailAddress?.address ?: username
        boolean sendAsAnotherUser = daemon || (username != fromAddress && fromAddress)
        if (sendAsAnotherUser) {
            log.debug "[GRAPH_EMAIL] [SEND_EMAIL] [PROCESS_ATTACHMENT_MSG] - Sending email as ${fromAddress}"
            // If sending as another user (daemon apps)
            userItemRequestBuilder = graphServiceClient.users().byUserId(fromAddress)
        }
        // Step 1: create a draft message
        Message draftMessage = userItemRequestBuilder.messages().post(message)
        log.debug("Draft created: id=${draftMessage.id}, changeKey=${draftMessage.changeKey}")
        int mbSize = 1024 * 1024 //size in MiB
        // Step 2: upload attachments (normal or via upload session)
        attachmentList.each { FileAttachment attachment ->
            attachment.odataType = '#microsoft.graph.fileAttachment'
            AttachmentItem attachmentItem = new AttachmentItem()
            attachmentItem.isInline = attachment.isInline
            attachmentItem.name = attachment.name
            attachmentItem.attachmentType = AttachmentType.File
            attachmentItem.contentType = attachment.contentType ?: "application/octet-stream"
            attachmentItem.size = attachment.contentBytes.length as Long
            attachmentItem.contentId = attachment.contentId
            if ((attachment.contentBytes.length / mbSize) > maxAttachmentSizeInMB) {
                log.debug("[GRAPH_EMAIL] [ATTACHMENT] Uploading '${attachment.name}' via draft upload as size is large")
                CreateUploadSessionPostRequestBody createUploadSessionPostRequestBody = new CreateUploadSessionPostRequestBody()
                createUploadSessionPostRequestBody.setAttachmentItem(attachmentItem)
                //more than 3MB size - send via upload session
                UploadSession uploadSession = userItemRequestBuilder
                        .messages().byMessageId(draftMessage.id)
                        .attachments()
                        .createUploadSession()
                        .post(createUploadSessionPostRequestBody)
                InputStream inputStream = new ByteArrayInputStream(attachment.contentBytes)
                LargeFileUploadTask<AttachmentItem> largeFileUploadTask = new LargeFileUploadTask(graphServiceClient.getRequestAdapter(), uploadSession, inputStream, inputStream.available().toLong(), new ParsableFactory<AttachmentItem>() {
                    @Override
                    AttachmentItem create(@jakarta.annotation.Nonnull ParseNode parseNode) {
                        return AttachmentItem.createFromDiscriminatorValue(parseNode)
                    }
                })
                //upload the file
                largeFileUploadTask.upload()

            } else {
                //less than 3MB size - send via normal upload
                log.debug("[GRAPH_EMAIL] [ATTACHMENT] [INLINE] Uploading '${attachment.name}' via normal post.")
                userItemRequestBuilder.messages().byMessageId(draftMessage.id).attachments()
                        .post(attachment)
            }
        }
        // Step 3: re-fetch the draft to get the latest changeKey
        Message refreshedDraft = userItemRequestBuilder.messages().byMessageId(draftMessage.id).get()
        log.debug("[GRAPH_EMAIL] [SEND_EMAIL] Draft refreshed: id=${refreshedDraft.id}, changeKey=${refreshedDraft.changeKey}")

        // Step 4: send the refreshed draft
        userItemRequestBuilder.messages().byMessageId(refreshedDraft.id).send().post()
        log.debug("[GRAPH_EMAIL] [SEND_EMAIL] [FINISHED] - Sent draft message ID: ${refreshedDraft.id}")
    }

    @CompileDynamic
    public void testConnection() throws ApiException {
        if (Holders.config.getProperty('grails.mail.oAuth.health.check.disabled', Boolean)) {
            log.warn("[GRAPH_EMAIL] [HEALTH_CHECK] Disabled via config.")
            return
        }
        log.debug("[GRAPH_EMAIL] [HEALTH_CHECK] Testing connection with current access token.")
        mailOAuthService.refreshAccessToken(mailOAuthService.tokenStore.getToken())
        log.info("[GRAPH_EMAIL] [HEALTH_CHECK] Token refresh successful.")
    }
}
