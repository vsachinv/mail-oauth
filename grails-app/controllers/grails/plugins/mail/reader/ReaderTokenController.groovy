package grails.plugins.mail.reader

class ReaderTokenController {

    def readerTokenStoreService

    def callback(String code, String state) {
        //Todo in actual implementation we would need to attach state with graphconfig so that callback can be received
        readerTokenStoreService.generateAccessTokenFor(null, code, state)
        render "generated token for code: ${code} state: ${state}"
    }

}
