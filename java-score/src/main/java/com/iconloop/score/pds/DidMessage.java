package com.iconloop.score.pds;

import java.math.BigInteger;

public class DidMessage {
    public final String did;
    public final String kid;
    public final String target;
    public BigInteger nonce;

    public DidMessage(String did, String kid, String target, BigInteger nonce) {
        this.did = did;
        this.kid = kid;
        this.target = target;
        this.nonce = nonce;
    }
}
