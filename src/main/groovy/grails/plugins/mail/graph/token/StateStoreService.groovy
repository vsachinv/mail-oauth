package grails.plugins.mail.graph.token

import groovy.transform.CompileStatic

@CompileStatic
interface StateStoreService {

    void storeState(String id, String state)

    String getIdForState(String state)

}
