package grails.plugins.mail.graph.sender

import grails.plugins.mail.graph.token.StateStoreService
import grails.web.api.ServletAttributes
import groovy.transform.CompileStatic


@CompileStatic
class SessionStateStoreService implements StateStoreService, ServletAttributes {

    @Override
    void storeState(String id, String state) {
        getSession().setAttribute(state, id)
    }

    @Override
    String getIdForState(String state) {
        return getSession().getAttribute(state)
    }
}
