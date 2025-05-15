package com.parametacorp.jwt;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

public class Payload {
    private final JsonObject payload;

    public Payload(JsonObject payload) {
        this.payload = payload;
    }

    public boolean validate(JsonObject actual, long currentHeight) {
        if (actual == null) {
            return false;
        }
        if (actual.get("method").asString().equals(payload.get("method").asString())) {
            JsonObject expectedParams = payload.get("params").asObject();
            JsonObject actualParams = actual.get("params").asObject();
            if (expectedParams.size() != actualParams.size()) {
                return false;
            }
            for (String key : expectedParams.names()) {
                if (!actualParams.contains(key)) {
                    return false;
                }
                JsonValue expected = expectedParams.get(key);
                JsonValue actualValue = actualParams.get(key);
                if (expected.isString()) {
                    if (!actualValue.isString() || !expected.asString().equals(actualValue.asString())) {
                        return false;
                    }
                } else if (expected.isNumber()) {
                    if (!actualValue.isNumber()) {
                        return false;
                    }
                    if (key.equals("base_height") &&
                            (actualValue.asLong() < expected.asLong() || currentHeight <= actualValue.asLong())) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return payload.toString();
    }

    public static class Builder {
        private final String method;
        private String labelId;
        private String dataId;
        private long baseHeight;

        public Builder(String method) {
            this.method = method;
        }

        public Builder labelId(String labelId) {
            this.labelId = labelId;
            return this;
        }

        public Builder dataId(String data) {
            this.dataId = data;
            return this;
        }

        public Builder baseHeight(long height) {
            this.baseHeight = height;
            return this;
        }

        public Payload build() {
            JsonObject params = Json.object();
            addIfNotNull(params, "label_id", Json.value(labelId));
            addIfNotNull(params, "data_id", Json.value(dataId));
            if (baseHeight > 0) {
                params.add("base_height", Json.value(baseHeight));
            }

            JsonObject payload = Json.object()
                    .add("method", method)
                    .add("params", params);
            return new Payload(payload);
        }

        private void addIfNotNull(JsonObject params, String name, JsonValue value) {
            if (value != Json.NULL) {
                params.add(name, value);
            }
        }
    }
}
