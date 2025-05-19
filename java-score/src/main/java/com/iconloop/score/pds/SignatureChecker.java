package com.iconloop.score.pds;

import com.eclipsesource.json.JsonObject;
import com.parametacorp.jwt.Jwt;
import com.parametacorp.jwt.Payload;
import score.Address;
import score.Context;

public class SignatureChecker {
    private String ownerId;
    private JsonObject payload;

    public boolean verifySig(Address didScore, String ownerSig) {
        var jwt = new Jwt(ownerSig);
        String[] tokens = jwt.parseHeader();
        ownerId = tokens[0];
        String kid = tokens[1];

        byte[] pubKey = Context.call(byte[].class, didScore, "getPublicKey", ownerId, kid);
        if (jwt.verify(pubKey)) {
            this.payload = jwt.getPayload();
            return true;
        }
        return false;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public boolean validatePayload(Payload expected) {
        return expected.validate(payload, Context.getBlockHeight());
    }
}
