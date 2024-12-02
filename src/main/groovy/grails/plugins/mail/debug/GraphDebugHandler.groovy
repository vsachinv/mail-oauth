package grails.plugins.mail.debug

import java.nio.charset.StandardCharsets;

import okhttp3.Response;
import okio.Buffer;

class GraphDebugHandler implements okhttp3.Interceptor {

    private String name

    GraphDebugHandler(){
        this.name = 'default'
    }

    GraphDebugHandler(String name){
        this.name = name
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        System.out.println("===================================Start====for ${name}=========");
        System.out.printf("Request: %s %s%n", chain.request().method(), chain.request().url().toString());
        System.out.println("Request headers:");
        chain.request().headers().toMultimap()
                .forEach((k, v) -> System.out.printf("%s: %s%n", k, String.join(", ", v)));
        if (chain.request().body() != null) {
            System.out.println("Request body:");
            final Buffer buffer = new Buffer();
            chain.request().body().writeTo(buffer);
            System.out.println(buffer.readString(StandardCharsets.UTF_8));
        }

        final Response response = chain.proceed(chain.request());

        System.out.println("-------------------");
        System.out.printf("Response: %s%n", response.code());
        System.out.println("Response headers:");
        response.headers().toMultimap()
                .forEach((k, v) -> System.out.printf("%s: %s%n", k, String.join(", ", v)));
        if (response.body() != null) {
            System.out.println("Response body:");
            System.out.println(response.peekBody(Long.MAX_VALUE).string());
        }
        System.out.println("===================================END=======for ${name}======");
        return response;
    }
}