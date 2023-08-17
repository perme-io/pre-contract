package com.iconloop.score.pds;

public class DidMessage {
    public final String did;
    public final String kid;
    public final String target;
    public int nonce;

    public DidMessage(String did, String kid, String target, int nonce) {
        this.did = did;
        this.kid = kid;
        this.target = target;
        this.nonce = nonce;
    }
}
