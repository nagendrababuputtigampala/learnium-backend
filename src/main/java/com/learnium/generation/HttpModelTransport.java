package com.learnium.generation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * The real transport: a thin {@link HttpClient} wrapper with a 10-second
 * connect timeout and the per-request timeout from the spec. Kept
 * deliberately small — everything interesting (URLs, bodies, parsing,
 * failure classification) lives in the adapters, which are tested
 * against a fake transport.
 */
@Component
public class HttpModelTransport implements ModelTransport {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;

    public HttpModelTransport() {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    }

    HttpModelTransport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public ModelResponse send(ModelRequest request) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(request.url()))
                        .timeout(Duration.ofSeconds(request.timeoutSeconds()))
                        .POST(HttpRequest.BodyPublishers.ofString(request.jsonBody()));
        request.headers().forEach(builder::header);
        try {
            HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ModelTransportException(response.statusCode());
            }
            return new ModelResponse(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new ModelTransportException(-1, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModelTransportException(-1, e);
        }
    }
}
