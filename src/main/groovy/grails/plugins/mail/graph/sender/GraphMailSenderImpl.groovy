package grails.plugins.mail.graph.sender

import com.microsoft.graph.models.AttachmentItem
import com.microsoft.graph.models.AttachmentType
import com.microsoft.graph.models.FileAttachment
import com.microsoft.graph.models.UploadSession
import com.microsoft.graph.serviceclient.GraphServiceClient
import com.microsoft.graph.core.tasks.LargeFileUploadTask
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

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) throws MailException {
        log.warn("Invalid method of GraphMailSenderImpl getting called")
        throw new GrailsMailException("Please do use sendMailViaGraph method rather doSend")
    }

    void sendMailViaGraph(Message message, List<FileAttachment> attachmentList) throws MailException {
        Map<Object, Exception> failedMessages = new LinkedHashMap<Object, Exception>()
        boolean connectionStatus = mailOAuthService.accessToken
        try {
            if (!connectionStatus) {
                throw new MailAuthenticationException(new AuthenticationFailedException())
            }
        } catch (Exception ex) {
            failedMessages.put(message, ex)
            throw new MailSendException("Mail server connection failed", ex, failedMessages)
        }
        try {
            processAttachmentAndSendMsg(message, attachmentList)
        } catch (ApiException ex) {
            log.error("Graph exception occurred, and message is : ${ex.message}")
            failedMessages.put(message, ex)
        } finally {
            if (!connectionStatus) {
                log.error("Failed to connect to MS Graph API, please check app configuration and refresh token validity.")
            }
        }
        if (!failedMessages.isEmpty()) {
            throw new MailSendException(failedMessages)
        }
    }

    private void processAttachmentAndSendMsg(Message message, List<FileAttachment> attachmentList) throws ApiException {
        log.debug("Sending email to ${message.toRecipients ? (message?.toRecipients*.emailAddress*.address) : ''} with ${attachmentList?.size()} attachments using graph protocol")
        GraphServiceClient graphServiceClient = graphApiClient.standardMailClient
        message.attachments = []
        //create a draft message
        Message draftMessage = graphServiceClient.me().messages().post(message)
        int mbSize = 1024 * 1024 //size in MiB
        attachmentList.each { FileAttachment attachment ->
            attachment.odataType = '#microsoft.graph.fileAttachment'
            AttachmentItem attachmentItem = new AttachmentItem()
            attachmentItem.isInline = attachment.isInline
            attachmentItem.name = attachment.name
            attachmentItem.attachmentType = AttachmentType.File
            attachmentItem.contentType = attachment.contentType ?: "application/octet-stream"
            attachmentItem.size = attachment.contentBytes.length as Long
            if ((attachment.contentBytes.length / mbSize) > maxAttachmentSizeInMB) {
                CreateUploadSessionPostRequestBody createUploadSessionPostRequestBody = new CreateUploadSessionPostRequestBody()
                createUploadSessionPostRequestBody.setAttachmentItem(attachmentItem)
                //more than 3MB size - send via upload session
                UploadSession uploadSession = graphServiceClient.me()
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
                graphServiceClient.me().messages().byMessageId(draftMessage.id).attachments()
                        .post(attachment)
            }
        }
        draftMessage.sentDateTime = OffsetDateTime.now(Clock.systemUTC())
        //send out the draft message
        graphServiceClient.me().messages().byMessageId(draftMessage.id).send().post()
        log.debug("Sent email successfully")
    }

    @CompileDynamic
    public void testConnection() throws ApiException {
        if (Holders.config.getProperty('grails.mail.oAuth.health.check.disabled', Boolean)) {
            log.warn("Health Check is disabled by config")
            return
        }
        mailOAuthService.refreshAccessToken(mailOAuthService.tokenStore.getToken())
    }
}
