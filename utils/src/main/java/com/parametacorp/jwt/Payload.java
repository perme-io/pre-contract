package com.parametacorp.jwt;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

public class Payload {
    static final String KEY_METHOD = "method";
    static final String KEY_PARAM = "param";

    private final JsonObject payload;

    public Payload(JsonObject payload) {
        this.payload = payload;
    }

    public boolean validate(JsonObject actual, long currentHeight) {
        if (actual == null) {
            return false;
        }
        if (actual.get(KEY_METHOD).asString().equals(payload.get(KEY_METHOD).asString())) {
            JsonObject expectedParams = payload.get(KEY_PARAM).asObject();
            JsonObject actualParams = actual.get(KEY_PARAM).asObject();
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
                    if (key.equals("base_height")) {
                        if (actualValue.asLong() < expected.asLong() || currentHeight <= actualValue.asLong()) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return payload.toString();
    }

    public static class Builder {
        private final String method;
        private String labelId;
        private String dataId;
        private String policyId;
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

        public Builder policyId(String policyId) {
            this.policyId = policyId;
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
            addIfNotNull(params, "policy_id", Json.value(policyId));
            if (baseHeight > 0) {
                params.add("base_height", Json.value(baseHeight));
            }

            JsonObject payload = Json.object()
                    .add(KEY_METHOD, method)
                    .add(KEY_PARAM, params);
            return new Payload(payload);
        }

        private void addIfNotNull(JsonObject params, String name, JsonValue value) {
            if (value != Json.NULL) {
                params.add(name, value);
            }
        }
    }
}
