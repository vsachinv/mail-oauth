package grails.plugins.tenant

interface TenantContextProvider {
    Long getCurrentTenantId()
}