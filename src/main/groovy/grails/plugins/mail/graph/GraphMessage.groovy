package grails.plugins.mail.graph

import com.microsoft.graph.models.BodyType
import com.microsoft.graph.models.EmailAddress
import com.microsoft.graph.models.ItemBody
import com.microsoft.graph.models.Message
import com.microsoft.graph.models.Recipient
import groovy.util.logging.Slf4j
import org.springframework.mail.MailMessage
import org.springframework.mail.MailParseException
import java.time.ZoneOffset

@Slf4j
class GraphMessage extends Message implements MailMessage {

    void setFrom(Recipient from) throws MailParseException {
        if (!from) {
            return
        }
        super.from = from
    }

    @Override
    void setFrom(String from) throws MailParseException {
        if (!from) {
            return
        }
        Recipient fromEmailAddress = new Recipient()
        fromEmailAddress.emailAddress = new EmailAddress(address: from)
        super.from = fromEmailAddress
    }

    void setReplyTo(List<Recipient> replyToList) throws MailParseException {
        if (!replyToList) {
            return
        }
        super.replyTo = replyToList
    }


    @Override
    void setReplyTo(String replyTo) throws MailParseException {
        if (!replyTo) {
            return
        }
        LinkedList<Recipient> replyToList = new LinkedList<Recipient>()
        Recipient toRecipients = new Recipient()
        toRecipients.emailAddress = new EmailAddress(address: replyTo)
        replyToList.add(toRecipients)
        super.replyTo = replyToList
    }

    @Override
    void setTo(String to) throws MailParseException {
        if (!to) {
            return
        }
        LinkedList<Recipient> toRecipientsList = new LinkedList<Recipient>()
        Recipient toRecipients = new Recipient()
        toRecipients.emailAddress = new EmailAddress(address: to)
        toRecipientsList.add(toRecipients)
        super.replyTo = toRecipientsList
    }

    @Override
    void setTo(String[] to) throws MailParseException {
        if (!to) {
            return
        }
        LinkedList<Recipient> toRecipientsList = new LinkedList<Recipient>()
        to.each { it ->
            Recipient toRecipients = new Recipient()
            toRecipients.emailAddress = new EmailAddress(address: it)
            toRecipientsList.add(toRecipients)
        }
        super.toRecipients = toRecipientsList

    }

    @Override
    void setCc(String cc) throws MailParseException {
        if (!cc) {
            return
        }
        LinkedList<Recipient> ccRecipientsList = new LinkedList<Recipient>()
        Recipient toRecipients = new Recipient()
        toRecipients.emailAddress = new EmailAddress(address: cc)
        ccRecipientsList.add(toRecipients)
        super.ccRecipients = ccRecipientsList
    }

    @Override
    void setCc(String[] cc) throws MailParseException {
        if (!cc) {
            return
        }
        LinkedList<Recipient> ccRecipientsList = new LinkedList<Recipient>()
        cc.each { it ->
            Recipient toRecipients = new Recipient()
            toRecipients.emailAddress = new EmailAddress(address: it)
            ccRecipientsList.add(toRecipients)
        }
        super.ccRecipients = ccRecipientsList
    }

    @Override
    void setBcc(String bcc) throws MailParseException {
        if (!bcc) {
            return
        }
        LinkedList<Recipient> bccRecipientsList = new LinkedList<Recipient>()
        Recipient toRecipients = new Recipient()
        toRecipients.emailAddress = new EmailAddress(address: bcc)
        bccRecipientsList.add(toRecipients)
        super.bccRecipients = bccRecipientsList
    }

    @Override
    void setBcc(String[] bcc) throws MailParseException {
        if (!bcc) {
            return
        }
        LinkedList<Recipient> bccRecipientsList = new LinkedList<Recipient>()
        bcc.each { it ->
            Recipient toRecipients = new Recipient()
            toRecipients.emailAddress = new EmailAddress(address: it)
            bccRecipientsList.add(toRecipients)
        }
        super.bccRecipients = bccRecipientsList
    }

    @Override
    void setSentDate(Date sentDate) throws MailParseException {
        if (!sentDate) {
            return
        }
        super.sentDateTime = sentDate.toInstant()
                .atOffset(ZoneOffset.UTC)
    }

    @Override
    void setSubject(String subject) throws MailParseException {
        if (subject == null) {
            return
        }
        super.subject = subject
    }

    @Override
    void setText(String text) throws MailParseException {
        if (text == null) {
            return
        }
        ItemBody itemBody = new ItemBody()
        itemBody.contentType = BodyType.TEXT
        itemBody.content = text
        super.body = itemBody
    }



}
