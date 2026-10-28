package grails.plugins.mail.oauth

import grails.testing.web.controllers.ControllerUnitTest
import org.springframework.mail.MailAuthenticationException
import org.springframework.mail.MailMessage
import spock.lang.Specification

/** sendTestMail outcomes: sent, disabled (sendMail returns null), invalid address, mapped failures. */
class MailOAuthControllerSpec extends Specification implements ControllerUnitTest<MailOAuthController> {

    private static final String INDEX = '/mailOAuth/index'

    /**
     * The Grails Mail plugin routes a controller's sendMail {} to the 'mailService' bean. This stub stands in
     * for it; 'behaviour' is what the real MailService.sendMail would do (return a MailMessage, null when
     * grails.mail.disabled is set, or throw).
     */
    static class StubMailService extends grails.plugins.mail.MailService {
        static Closure<Object> behaviour = { null }
        static boolean invoked = false

        @Override
        MailMessage sendMail(Closure c) { invoked = true; behaviour.call() as MailMessage }

        // The real service builds a thread pool from MailConfigurationProperties; not needed here.
        @Override
        void afterPropertiesSet() {}

        @Override
        void destroy() {}
    }

    def setup() {
        StubMailService.behaviour = { null }
        StubMailService.invoked = false
        defineBeans { mailService(StubMailService) }
        controller.mailOAuthService = Mock(MailOAuthService)
        config.merge([grails: [mail: [oAuth: [enabled: true, 'redirect.uri': INDEX]]]])
        controller.setConfiguration(config)
    }

    void "sendTestMail reports success when the mail plugin returns a message"() {
        given:
        StubMailService.behaviour = { Mock(MailMessage) }

        when:
        controller.sendTestMail('test@example.com')

        then:
        flash.message == 'Test mail sent to test@example.com'
        !flash.warn
        response.redirectedUrl == INDEX
    }

    void "sendTestMail reports disabled when the mail plugin returns null (grails.mail.disabled)"() {
        given:
        StubMailService.behaviour = { null }

        when:
        controller.sendTestMail('test@example.com')

        then:
        flash.warn == 'Mail sending is disabled by configuration. Test mail was not sent.'
        !flash.message
        !flash.error
        response.redirectedUrl == INDEX
    }

    void "sendTestMail rejects an invalid address before sending"() {
        given:
        StubMailService.behaviour = { null }

        when:
        controller.sendTestMail('invalid-email')

        then:
        !StubMailService.invoked
        flash.error.contains('invalid email address')
        response.redirectedUrl == INDEX
    }

    void "sendTestMail maps a mail authentication failure to a specific message"() {
        given:
        StubMailService.behaviour = { throw new MailAuthenticationException('auth failed') }

        when:
        controller.sendTestMail('test@example.com')

        then:
        flash.error.contains('Authentication failed')
        response.redirectedUrl == INDEX
    }
}
