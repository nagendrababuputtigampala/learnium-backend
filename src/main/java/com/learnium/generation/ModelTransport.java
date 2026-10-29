package com.learnium.generation;

import java.util.Map;

/**
 * The seam between provider adapters and the network: an adapter builds
 * a request spec, the transport performs it, the adapter parses the
 * response spec. Request building and response parsing stay pure and
 * unit-testable offline; only {@link HttpModelTransport} touches a
 * socket, and tests never call it.
 */
public interface ModelTransport {

    /** Sends one request; returns only for 2xx, throws {@link ModelTransportException} otherwise. */
    ModelResponse send(ModelRequest request);

    record ModelRequest(String url, Map<String, String> headers, String jsonBody, long timeoutSeconds) {

        public ModelRequest {
            headers = Map.copyOf(headers);
        }
    }

    record ModelResponse(int statusCode, String body) {}
}
