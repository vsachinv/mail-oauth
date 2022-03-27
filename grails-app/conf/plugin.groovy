grails {
    mail {
        oAuth {
            enabled = false
            client_id = 'b5351907-3792-4590-ac3a-ee9ba737e7d1'
            secret_val = 'DvW7Q~CaMbkKrX2DDifpHQbOOasr3k-RrmSAN'
            api_scope = 'https://outlook.office.com/SMTP.Send offline_access'
            callback_url = 'http://localhost:9090/mailOAuth/callback'
            tenant_id = 'common'
            token {
                refresh.frequency = 300 //every 5mins
                refresh.time.difference = 600 //if less than 10mins then refresh
            }
            redirect.uri = '/mailOAuth/index'
        }
    }
}