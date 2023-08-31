package com.iconloop.score.pds;

import score.Address;

import java.math.BigInteger;

public class DidMessage {
    public final String did;
    public final String kid;
    public final Address from;
    public final String target;
    public final String method;
    public final BigInteger lastUpdated;
    public BigInteger nonce;

    public DidMessage(String did, String kid, Address from, String target, String method, BigInteger lastUpdated, BigInteger nonce) {
        this.did = did;
        this.kid = kid;
        this.from = from;
        this.target = target;
        this.method = method;
        this.lastUpdated = lastUpdated;
        this.nonce = nonce;
    }
}
