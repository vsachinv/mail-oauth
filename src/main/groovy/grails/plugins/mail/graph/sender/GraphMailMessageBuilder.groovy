package grails.plugins.mail.graph.sender

import com.microsoft.graph.models.BodyType
import com.microsoft.graph.models.EmailAddress
import com.microsoft.graph.models.FileAttachment
import com.microsoft.graph.models.Importance
import com.microsoft.graph.models.InferenceClassificationType
import com.microsoft.graph.models.InternetMessageHeader
import com.microsoft.graph.models.ItemBody
import com.microsoft.graph.models.Recipient
import grails.plugins.mail.GrailsMailException
import grails.plugins.mail.MailConfigurationProperties
import grails.plugins.mail.MailMessageBuilder
import grails.plugins.mail.MailMessageContentRenderer
import grails.plugins.mail.graph.GraphMessage
import grails.web.mime.MimeType
import groovy.util.logging.Slf4j
import org.apache.commons.io.FilenameUtils
import org.grails.web.mime.DefaultMimeUtility
import org.springframework.core.io.InputStreamSource
import org.springframework.mail.MailMessage
import org.springframework.mail.MailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.util.StreamUtils

import java.util.concurrent.ExecutorService

@Slf4j
class GraphMailMessageBuilder extends MailMessageBuilder {

    public List<Recipient> bccRecipients = []
    public ItemBody body = null
    public String bodyPreview
    public List<Recipient> ccRecipients = []
    public Recipient from = null
    public Boolean hasAttachments = false
    public Importance importance = null
    public InferenceClassificationType inferenceClassification = null
    public List<InternetMessageHeader> internetMessageHeaders = null
    public List<Recipient> replyTo = []
    public Recipient sender = null
    public List<Recipient> toRecipients = []
    public String subject = null
    public int multipart
    public boolean hasHTMLBody = false
    public List<FileAttachment> attachmentList = []
    private boolean async = false

    private final DefaultMimeUtility grailsMimeUtility

    GraphMailMessageBuilder(MailSender mailSender, MailConfigurationProperties properties, MailMessageContentRenderer mailMessageContentRenderer, grailsMimeUtility) {
        super(mailSender, properties, mailMessageContentRenderer)
        this.grailsMimeUtility = grailsMimeUtility
    }

    void processGraphMessage() {
        this.multipart = this.multipart ?: MimeMessageHelper.MULTIPART_MODE_NO //not supported in graph SDK
        this.attachmentList = this.attachmentList ?: []
        this.hasAttachments = this.hasAttachments ?: false
        this.importance = this.importance ?: Importance.NORMAL
        this.inferenceClassification = this.inferenceClassification ?: InferenceClassificationType.FOCUSED
    }

    @Override
    void headers(Map hdrs) {
        LinkedList<InternetMessageHeader> internetMessageHeadersList = new LinkedList<InternetMessageHeader>()
        hdrs.each { it ->
            InternetMessageHeader internetMessageHeaders = new InternetMessageHeader(name: 'x-' + it.key as String, value: it.value as String)
            internetMessageHeadersList.add(internetMessageHeaders)
        }
        this.internetMessageHeaders = internetMessageHeadersList
    }

    /*
    support 255 character for body preview if body have text content not HTML
    */

    void bodyPreview(CharSequence bPreview) {
        String mailBody = String.valueOf(bPreview)
        this.bodyPreview = mailBody.size() > 255 ? mailBody.substring(0, 254) : mailBody
    }

    @Override
    void body(CharSequence mailBody) {
        if (mailBody == null) {
            return
        }
        ItemBody itemBody = new ItemBody()
        itemBody.contentType = this.hasHTMLBody ? BodyType.HTML : BodyType.TEXT
        itemBody.content = String.valueOf(mailBody)
        this.body = itemBody
    }

    @Override
    void html(CharSequence htmlContent) {
        if (htmlContent == null) {
            return
        }
        this.hasHTMLBody = true
        body(htmlContent)
    }

    @Override
    void text(CharSequence textContent) {
        if (textContent == null) {
            return
        }
        this.hasHTMLBody = false
        body(textContent)
        bodyPreview(textContent)
    }

    @Override
    void bcc(Object[] args) {
        if (!args) {
            return
        }
        LinkedList<Recipient> bccRecipientsList = new LinkedList<Recipient>()
        args.each { it ->
            Recipient toRecipients = new Recipient()
            toRecipients.emailAddress = new EmailAddress(address: it as String)
            bccRecipientsList.add(toRecipients)
        }
        this.bccRecipients = bccRecipientsList
    }


    @Override
    void bcc(List bcc) {
        if (!bcc) {
            return
        }
        LinkedList<Recipient> bccRecipientsList = new LinkedList<Recipient>()
        bcc.each { it ->
            Recipient toRecipients = new Recipient()
            toRecipients.emailAddress = new EmailAddress(address: it as String)
            bccRecipientsList.add(toRecipients)
        }
        this.bccRecipients = bccRecipientsList
    }


    void cc(Object[] args) {
        if (!args) {
            return
        }
        LinkedList<Recipient> ccRecipientsList = new LinkedList<Recipient>()
        args.each { it ->
            Recipient toRecipients = new Recipient()
            toRecipients.emailAddress = new EmailAddress(address: it as String)
            ccRecipientsList.add(toRecipients)
        }
        this.ccRecipients = ccRecipientsList
    }

    @Override
    void cc(List cc) {
        if (!cc) {
            return
        }
        LinkedList<Recipient> ccRecipientsList = new LinkedList<Recipient>()
        cc.each { it ->
            Recipient toRecipients = new Recipient()
            toRecipients.emailAddress = new EmailAddress(address: it as String)
            ccRecipientsList.add(toRecipients)
        }
        this.ccRecipients = ccRecipientsList
    }

    /*
    set importance of the email
    valid values are -1 for low, 1 for high and normal for any other value
    Available in Graph but not in grails mail
    */

    @SuppressWarnings("unused")
    void importance(int priority) {
        switch (priority) {
            case 0: this.importance = Importance.LOW
                break
            case 1: this.importance = Importance.HIGH
                break
            default: this.importance = Importance.NORMAL
        }
    }

    /*
    set mailbox folder classification of the email
    valid values are 0 for Other, Focused for any other value
    Available in Graph but not in grails mail
    */

    @SuppressWarnings("unused")
    void inferenceClassification(int mailBoxFolder) {
        switch (mailBoxFolder) {
            case 0: this.inferenceClassification = InferenceClassificationType.OTHER
                break
            default: this.inferenceClassification = InferenceClassificationType.FOCUSED
        }
    }

    @Override
    void replyTo(CharSequence replyTo) {
        if (!replyTo) {
            return
        }
        LinkedList<Recipient> replyToList = new LinkedList<Recipient>()
        Recipient toRecipients = new Recipient()
        toRecipients.emailAddress = new EmailAddress(address: String.valueOf(replyTo))
        replyToList.add(toRecipients)
        this.replyTo = replyToList
    }

    /*
    Available in Graph but not in grail mail plugin
    */

    @SuppressWarnings("unused")
    void replyToAll(List<String> replyTos) {
        if (!replyTos) {
            return
        }
        LinkedList<Recipient> replyToList = new LinkedList<Recipient>()
        replyTos.each { it ->
            Recipient toRecipients = new Recipient()
            toRecipients.emailAddress = new EmailAddress(address: it)
            replyToList.add(toRecipients)
        }
        this.replyTo = replyToList
    }

    /*
    Available in Graph but not in grail mail plugin
    */

    @SuppressWarnings("unused")
    void sender(String senderMail) {
        if (!senderMail) {
            return
        }
        Recipient senderRecipient = new Recipient()
        senderRecipient.emailAddress = new EmailAddress(address: senderMail)
        this.sender = senderRecipient
    }

    @Override
    void from(CharSequence fromMail) {
        if (defaultFrom) {
            fromMail = defaultFrom
        }
        if (!fromMail) {
            return
        }
        Recipient fromEmailAddress = new Recipient()
        fromEmailAddress.emailAddress = new EmailAddress(address: String.valueOf(fromMail))
        this.from = fromEmailAddress
    }

    /*
    Not supported as of now in Graph sdk
    */
    /*@Override
    void envelopeFrom(CharSequence envelopeFrom) {
        this.envelopeFrom = String.valueOf(envelopeFrom)
    }*/

    @Override
    void multipart(boolean multipart) {
        this.multipart = MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED
    }

    @Override
    void to(Object[] args) {
        if (!args) {
            return
        }
        LinkedList<Recipient> toRecipientsList = new LinkedList<Recipient>()
        args.each { it ->
            Recipient toRecipients = new Recipient()
            toRecipients.emailAddress = new EmailAddress(address: it)
            toRecipientsList.add(toRecipients)
        }
        this.toRecipients = toRecipientsList
    }

    @Override
    void to(List tos) {
        if (!tos) {
            return
        }
        LinkedList<Recipient> toRecipientsList = new LinkedList<Recipient>()
        tos.each { it ->
            Recipient toRecipients = new Recipient()
            toRecipients.emailAddress = new EmailAddress(address: it)
            toRecipientsList.add(toRecipients)
        }
        this.toRecipients = toRecipientsList
    }

    @Override
    void subject(CharSequence subject) {
        this.subject = String.valueOf(subject)
    }

    String getDescription(GraphMessage message) {
        return "[${message.subject}] from [${message.from}] to ${message.toRecipients}"
    }

    @Override
    MailMessage sendMessage(ExecutorService executorService) {
        MailMessage message = finishMessage()
        List attachments = new LinkedList<FileAttachment>(this.attachmentList)
        if (log.traceEnabled) {
            log.trace("Sending mail ${getDescription(message)}} ...")
        }

        if (async) {
            executorService.execute({
                try {
                    mailSender.sendMailViaGraph(message, attachments)
                } catch (Throwable t) {
                    if (log.errorEnabled) log.error("Failed to send email", t)
                }
            } as Runnable)
        } else {
            mailSender.sendMailViaGraph(message, attachments)
        }
        if (log.traceEnabled) {
            log.trace("Sent mail ${getDescription(message)}} ...")
        }
        message
    }

    @Override
    MailMessage finishMessage() {
        MailMessage message = new GraphMessage()
        this.processGraphMessage()
        message.hasAttachments = this.hasAttachments
        message.importance = this.importance
        message.bodyPreview = this.bodyPreview
        message.toRecipients = filterAddresses(this.toRecipients)
        if (this.sender)
            message.sender = this.sender
        message.ccRecipients = filterAddresses(this.ccRecipients)
        message.bccRecipients = filterAddresses(this.bccRecipients)
        if (this.replyTo) {
            message.replyTo = this.replyTo
        }
        message.inferenceClassification = this.inferenceClassification
        message.subject = this.subject
        if (this.from)
            message.from = this.from
        message.internetMessageHeaders = this.internetMessageHeaders
        if (message.hasAttachments) {
            message.attachments = this.attachmentList
        }
        message.body = this.body

        if (defaultFrom) {
            message.from = defaultFrom
        }

        if (defaultTo) {
            message.setTo(defaultTo)
        }
        return message
    }

    List<Recipient> filterAddresses(List<Recipient> addresses) {
        if (overrideAddress && addresses) {
            LinkedList<Recipient> recipientsList = new LinkedList<Recipient>()
            addresses.each { it ->
                Recipient recipient = new Recipient()
                recipient.emailAddress = new EmailAddress(address: overrideAddress)
                recipientsList.add(recipient)
            }
            return recipientsList
        }
        return addresses
    }

    @Override
    void attach(String fileName, File file) {
        if (!mimeCapable) {
            throw new GrailsMailException("Message is not an instance of org.springframework.mail.javamail.MimeMessage, cannot attach bytes!")
        }

        attach(fileName, getContentType(file), file)
    }

    @Override
    void inline(String fileName, File file) {
        if (!mimeCapable) {
            throw new GrailsMailException("Message is not an instance of org.springframework.mail.javamail.MimeMessage, cannot attach bytes!")
        }

        inline(fileName, getContentType(file), file)
    }

    @Override
    void inline(String contentId, String contentType, InputStreamSource source) {
        this.hasAttachments = true
        FileAttachment attachment = new FileAttachment(name: contentId, contentType: contentType, contentBytes: StreamUtils.copyToByteArray(source.inputStream), isInline: true)
        this.attachmentList.add(attachment)
    }

    @Override
    protected doAdd(String id, String contentType, InputStreamSource toAdd, boolean isAttachment) {
        this.hasAttachments = true
        FileAttachment attachment = new FileAttachment(name: id, contentType: contentType, contentBytes: StreamUtils.copyToByteArray(toAdd.inputStream), isInline: false)
        this.attachmentList.add(attachment)
    }

    String getContentType(File file) {
        String extension = FilenameUtils.getExtension(file.name)?.toLowerCase()
        MimeType mimeType = grailsMimeUtility.getMimeTypeForExtension(extension)
        if (mimeType) {
            return mimeType.name
        }
        return 'application/octet-stream'
    }

}
