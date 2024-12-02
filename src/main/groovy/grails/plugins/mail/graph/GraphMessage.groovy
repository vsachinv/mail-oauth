package grails.plugins.mail.graph

import com.microsoft.graph.models.BodyType
import com.microsoft.graph.models.EmailAddress
import com.microsoft.graph.models.ItemBody
import com.microsoft.graph.models.Message
import com.microsoft.graph.models.Recipient
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.mail.MailMessage
import org.springframework.mail.MailParseException
import java.time.ZoneOffset

@Slf4j
@CompileStatic
class GraphMessage extends Message implements MailMessage {
    //Due to groovy 3.x issue https://issues.apache.org/jira/browse/GROOVY-6653. Message class methods not overridden.
    @Override
    void setFrom(String from) throws MailParseException {
        if (from == null) {
            if (getFrom())
                super.setFrom(null)
            return
        }
        Recipient fromEmailAddress = new Recipient()
        fromEmailAddress.emailAddress = new EmailAddress(address: from)
        super.setFrom(fromEmailAddress)
    }


    @Override
    void setReplyTo(String replyTo) throws MailParseException {
        if (replyTo == null) {
            if (getReplyTo())
                super.setReplyTo(null)
            return
        }
        LinkedList<Recipient> replyToList = new LinkedList<Recipient>()
        Recipient toRecipients = new Recipient()
        toRecipients.emailAddress = new EmailAddress(address: replyTo)
        replyToList.add(toRecipients)
        super.setReplyTo(replyToList)
    }

    @Override
    void setTo(String to) throws MailParseException {
        if (to == null) {
            if (toRecipients) {
                super.setToRecipients(null)
            }
            return
        }
        LinkedList<Recipient> toRecipientsList = new LinkedList<Recipient>(toRecipients ?: [])
        Recipient toRecipients = new Recipient()
        toRecipients.emailAddress = new EmailAddress(address: to)
        toRecipientsList.add(toRecipients)
        super.setToRecipients(toRecipientsList)
    }

    @Override
    void setTo(String[] to) throws MailParseException {
        if (to == null) {
            if (toRecipients)
                super.setToRecipients(null)
            return
        }
        LinkedList<Recipient> toRecipientsList = new LinkedList<Recipient>(toRecipients ?: [])
        to.each { it ->
            Recipient toRecipients = new Recipient()
            toRecipients.emailAddress = new EmailAddress(address: it as String)
            toRecipientsList.add(toRecipients)
        }
        super.setToRecipients(toRecipientsList)

    }

    @Override
    void setCc(String cc) throws MailParseException {
        if (cc == null) {
            if (ccRecipients)
                super.setCcRecipients(null)
            return
        }
        LinkedList<Recipient> ccRecipientsList = new LinkedList<Recipient>(ccRecipients ?: [])
        Recipient toRecipients = new Recipient()
        toRecipients.emailAddress = new EmailAddress(address: cc)
        ccRecipientsList.add(toRecipients)
        super.setCcRecipients(ccRecipientsList)
    }

    @Override
    void setCc(String[] cc) throws MailParseException {
        if (cc == null) {
            if (ccRecipients)
                super.setCcRecipients(null)
            return
        }
        LinkedList<Recipient> ccRecipientsList = new LinkedList<Recipient>(ccRecipients ?: [])
        cc.each { it ->
            Recipient toRecipients = new Recipient()
            toRecipients.emailAddress = new EmailAddress(address: it as String)
            ccRecipientsList.add(toRecipients)
        }
        super.setCcRecipients(ccRecipientsList)
    }

    @Override
    void setBcc(String bcc) throws MailParseException {
        if (bcc == null) {
            if (bccRecipients)
                super.setBccRecipients(null)
            return
        }
        LinkedList<Recipient> bccRecipientsList = new LinkedList<Recipient>(bccRecipients ?: [])
        Recipient toRecipients = new Recipient()
        toRecipients.emailAddress = new EmailAddress(address: bcc)
        bccRecipientsList.add(toRecipients)
        super.setBccRecipients(bccRecipientsList)
    }

    @Override
    void setBcc(String[] bcc) throws MailParseException {
        if (bcc == null) {
            if (bccRecipients)
                super.setBccRecipients(null)
            return
        }
        LinkedList<Recipient> bccRecipientsList = new LinkedList<Recipient>(bccRecipients ?: [])
        bcc.each { it ->
            Recipient toRecipients = new Recipient()
            toRecipients.emailAddress = new EmailAddress(address: it as String)
            bccRecipientsList.add(toRecipients)
        }
        super.setBccRecipients(bccRecipientsList)
    }

    @Override
    void setSentDate(Date sentDate) throws MailParseException {
        if (sentDate == null) {
            if (getSentDateTime())
                super.setSentDateTime(null)
            return
        }
        super.setSentDateTime(sentDate.toInstant()
                .atOffset(ZoneOffset.UTC))
    }

    @Override
    void setSubject(String subject) throws MailParseException {
        super.setSubject(subject)
    }

    @Override
    void setText(String text) throws MailParseException {
        if (text == null) {
            if (body)
                super.setBody(null)
            return
        }
        ItemBody itemBody = new ItemBody()
        itemBody.contentType = BodyType.Text
        itemBody.content = text
        super.setBody(itemBody)
    }
}
