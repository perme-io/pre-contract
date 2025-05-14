package com.iconloop.score.pds.util;

import com.eclipsesource.json.Json;
import com.parametacorp.jwt.Payload;
import foundation.icon.did.core.Algorithm;
import foundation.icon.did.core.AlgorithmProvider;
import foundation.icon.did.core.DidKeyHolder;
import foundation.icon.did.document.EncodeType;
import foundation.icon.did.exceptions.AlgorithmException;

public class Jwt {
    private static final Algorithm algorithm = AlgorithmProvider.create(AlgorithmProvider.Type.ES256K);

    private final String header;
    private final String payload;

    public Jwt(String header, String payload) {
        this.header = header;
        this.payload = payload;
    }

    public String sign(DidKeyHolder keyHolder) throws AlgorithmException {
        String headerBase64 = EncodeType.BASE64URL.encode(header.getBytes());
        String payloadBase64 = EncodeType.BASE64URL.encode(payload.getBytes());
        byte[] sig = algorithm.sign(keyHolder.getPrivateKey(), (headerBase64 + "." + payloadBase64).getBytes());
        String sigBase64 = EncodeType.BASE64URL.encode(sig);
        return headerBase64 + "." + payloadBase64 + "." + sigBase64;
    }

    public static final class Builder {
        private final String kid;
        private Payload payload;

        public Builder(String kid) {
            this.kid = kid;
        }

        public Builder payload(Payload payload) {
            this.payload = payload;
            return this;
        }

        public Jwt build() {
            String header = Json.object()
                    .add("alg", "ES256K")
                    .add("kid", kid)
                    .toString();
            return new Jwt(header, payload.toString());
        }
    }
}
