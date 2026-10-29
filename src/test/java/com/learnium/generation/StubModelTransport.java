package com.learnium.generation;

import com.learnium.generation.ModelTransport.ModelRequest;
import com.learnium.generation.ModelTransport.ModelResponse;

/** Test double for the transport seam: records the request, replays a canned outcome. */
final class StubModelTransport implements ModelTransport {

    private ModelResponse response = new ModelResponse(200, "{}");
    private RuntimeException failure;
    private ModelRequest captured;

    StubModelTransport responding(String body) {
        this.response = new ModelResponse(200, body);
        return this;
    }

    StubModelTransport failing(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    @Override
    public ModelResponse send(ModelRequest request) {
        this.captured = request;
        if (failure != null) {
            throw failure;
        }
        return response;
    }

    ModelRequest captured() {
        return captured;
    }
}
