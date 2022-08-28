package grails.plugins.mail.graph.sender

import com.microsoft.graph.http.GraphFatalServiceException
import com.microsoft.graph.http.GraphServiceException
import com.microsoft.graph.models.AttachmentCreateUploadSessionParameterSet
import com.microsoft.graph.models.AttachmentItem
import com.microsoft.graph.models.AttachmentType
import com.microsoft.graph.models.FileAttachment
import com.microsoft.graph.models.UploadSession
import com.microsoft.graph.requests.GraphServiceClient
import com.microsoft.graph.tasks.LargeFileUploadTask
import grails.plugins.mail.GrailsMailException
import grails.plugins.mail.graph.GraphApiClient
import grails.plugins.mail.oauth.sender.OAuthMailSenderImpl
import grails.util.Holders
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
        } catch (GraphServiceException ex) {
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

    private void processAttachmentAndSendMsg(Message message, List<FileAttachment> attachmentList) throws GraphFatalServiceException {
        log.debug("Sending email to ${message.toRecipients ? (message?.toRecipients*.emailAddress*.address) : ''} with ${attachmentList?.size()} attachments using graph protocol")
        GraphServiceClient graphServiceClient = graphApiClient.standardMailClient
        message.attachments = null
        //create a draft message
        Message draftMessage = graphServiceClient.me().messages()
                .buildRequest()
                .post(message)
        int mbSize = 1024 * 1024 //size in MiB
        attachmentList.each { FileAttachment attachment ->
            AttachmentItem attachmentItem = new AttachmentItem()
            attachmentItem.isInline = attachment.isInline
            attachmentItem.name = attachment.name
            attachmentItem.attachmentType = AttachmentType.FILE
            attachmentItem.contentType = attachment.contentType ?: "application/octet-stream"
            attachmentItem.size = attachment.contentBytes.length as Long
            attachment.oDataType = '#microsoft.graph.fileAttachment'
            if ((attachment.contentBytes.length / mbSize) > maxAttachmentSizeInMB) {
                //more than 3MB size - send via upload session
                UploadSession uploadSession = graphServiceClient.me()
                        .messages(draftMessage.id)
                        .attachments()
                        .createUploadSession(AttachmentCreateUploadSessionParameterSet
                                .newBuilder()
                                .withAttachmentItem(attachmentItem)
                                .build())
                        .buildRequest()
                        .post()
                InputStream inputStream = new ByteArrayInputStream(attachment.contentBytes)
                LargeFileUploadTask<AttachmentItem> largeFileUploadTask = new LargeFileUploadTask(uploadSession, graphServiceClient, inputStream, inputStream.available(), AttachmentItem.class)
                //upload the file
                largeFileUploadTask.upload()

            } else {
                //less than 3MB size - send via normal upload
                graphServiceClient.me()
                        .messages(draftMessage.id)
                        .attachments()
                        .buildRequest()
                        .post(attachment)
            }
        }
        draftMessage.sentDateTime = OffsetDateTime.now(Clock.systemUTC())
        //send out the draft message
        graphServiceClient.me()
                .mailFolders("Drafts")
                .messages(draftMessage.id)
                .send()
                .buildRequest()
                .post()
        log.debug("Sent email successfully")
    }

    public void testConnection() throws GraphFatalServiceException {
        if (Holders.config.getProperty('grails.mail.oAuth.health.check.disabled', Boolean)) {
            log.warn("Health Check is disabled by config")
            return
        }
        mailOAuthService.refreshAccessToken(mailOAuthService.tokenStore.getToken())
    }
}
