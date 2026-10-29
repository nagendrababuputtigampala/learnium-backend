package com.learnium.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

/**
 * The real transport against a fake {@link HttpClient} — no sockets: the
 * fake replays a response or a failure, and the transport's status and
 * exception mapping is asserted.
 */
class HttpModelTransportTest {

    private static ModelTransport.ModelRequest request() {
        return new ModelTransport.ModelRequest(
                "http://localhost:9/generate",
                Map.of("x-goog-api-key", "k", "Content-Type", "application/json"),
                "{\"a\":1}",
                5);
    }

    @Test
    void twoHundredReturnsTheBodyAndSendsHeadersAndTimeout() {
        FakeHttpClient client = new FakeHttpClient();
        client.response = new FakeHttpResponse(200, "{\"ok\":true}");
        HttpModelTransport transport = new HttpModelTransport(client);

        ModelTransport.ModelResponse response = transport.send(request());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"ok\":true}");
        assertThat(client.lastRequest.headers().firstValue("x-goog-api-key")).hasValue("k");
        assertThat(client.lastRequest.timeout()).hasValue(Duration.ofSeconds(5));
        assertThat(client.lastRequest.method()).isEqualTo("POST");
    }

    @Test
    void errorStatusThrowsWithTheStatus() {
        FakeHttpClient client = new FakeHttpClient();
        client.response = new FakeHttpResponse(503, "unavailable");
        HttpModelTransport transport = new HttpModelTransport(client);

        assertThatThrownBy(() -> transport.send(request()))
                .isInstanceOf(ModelTransportException.class)
                .extracting(e -> ((ModelTransportException) e).getStatusCode())
                .isEqualTo(503);
    }

    @Test
    void ioFailureThrowsWithMinusOneAndTheCause() {
        FakeHttpClient client = new FakeHttpClient();
        client.ioFailure = new IOException("connection refused");
        HttpModelTransport transport = new HttpModelTransport(client);

        assertThatThrownBy(() -> transport.send(request()))
                .isInstanceOf(ModelTransportException.class)
                .satisfies(
                        e -> {
                            ModelTransportException failure = (ModelTransportException) e;
                            assertThat(failure.getStatusCode()).isEqualTo(-1);
                            assertThat(failure.getCause()).isInstanceOf(IOException.class);
                        });
    }

    @Test
    void interruptionThrowsAndRestoresTheInterruptFlag() {
        FakeHttpClient client = new FakeHttpClient();
        client.interrupt = true;
        HttpModelTransport transport = new HttpModelTransport(client);
        try {
            assertThatThrownBy(() -> transport.send(request()))
                    .isInstanceOf(ModelTransportException.class)
                    .extracting(e -> ((ModelTransportException) e).getStatusCode())
                    .isEqualTo(-1);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // Clear the flag so the test runner is unaffected.
            Thread.interrupted();
        }
    }

    /** An HttpClient that never touches the network. */
    private static final class FakeHttpClient extends HttpClient {

        private HttpResponse<String> response;
        private IOException ioFailure;
        private boolean interrupt;
        private HttpRequest lastRequest;

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
                throws IOException, InterruptedException {
            this.lastRequest = request;
            if (interrupt) {
                throw new InterruptedException("halted");
            }
            if (ioFailure != null) {
                throw ioFailure;
            }
            return (HttpResponse<T>) response;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return CompletableFuture.failedFuture(new UnsupportedOperationException());
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getInstance("TLS");
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
    }

    /** A canned HttpResponse with just the accessors the transport uses. */
    private record FakeHttpResponse(int status, String responseBody) implements HttpResponse<String> {

        @Override
        public int statusCode() {
            return status;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public String body() {
            return responseBody;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("http://localhost:9/generate");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
