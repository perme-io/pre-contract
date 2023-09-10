package com.iconloop.score.pds;

import score.Address;
import score.Context;
import scorex.util.Base64;
import scorex.util.StringTokenizer;

import java.math.BigInteger;

public class DidMessage {
    public final String version;
    public final String did;
    public final String kid;
    private Address from;
    private String target;
    private String method;
    private BigInteger lastUpdated;
    private byte[] hashedMessage;
    private byte[] signature;

    public DidMessage(String did, String kid, Address from, String target, String method, BigInteger lastUpdated) {
        this.version = "v1";
        this.did = did;
        this.kid = kid;
        this.from = from;
        this.target = target;
        this.method = method;
        this.lastUpdated = lastUpdated;
        this.hashedMessage = null;
    }

    public void update(Address from, String target, String method, BigInteger lastUpdated) {
        this.from = (from == null) ? this.from : from;
        this.target = (target == null) ? this.target : target;
        this.method = (method == null) ? this.method : method;
        this.lastUpdated = (lastUpdated == null) ? this.lastUpdated : lastUpdated;
    }

    public String getVersion() {
        return this.version;
    }

    public String getTarget() {
        return this.target;
    }

    public BigInteger getLastUpdated() {
        return this.lastUpdated;
    }

    public String getMessage() {
        return this.did + "#" + this.kid + "#" + this.version + "#" + new String(Base64.getEncoder().encode(this.hashedMessage));
    }

    public byte[] getMessageForHash() {
        String address = (this.from != null) ? this.from.toString() : "";
        String mergedMessage = this.did + "#" + this.kid + "#" + address + "#" + this.target + "#" + this.method + "#" + this.lastUpdated;
        return mergedMessage.getBytes();
    }

    public void setHashedMessage(byte[] hashedMessage) {
        this.hashedMessage = hashedMessage;
    }

    public byte[] getHashedMessage() {
        return this.hashedMessage;
    }

    public void setSignature(byte[] recoverableSerialize) {
        this.signature = recoverableSerialize;
    }

    public byte[] getSignature() {
        return this.signature;
    }

    public static DidMessage parse(String message) {
        String[] did_info = new String[4];
        StringTokenizer st = new StringTokenizer(message, "#");
        int countTokens = st.countTokens();

        int index = 0;
        while (st.hasMoreTokens()) {
            did_info[index++] = st.nextToken();
        }

        String did = message;
        String kid = "publicKey";
        String version = "";
        String hashedMessage = "";

        if (countTokens >= 2) {
            did = did_info[0];
            kid = did_info[1];
        }

        if (countTokens == 4) {
            version = did_info[2];
            hashedMessage = did_info[3];
        }

        DidMessage didMessage = new DidMessage(did, kid, null, "", "", BigInteger.ZERO);
        if (!version.equals(didMessage.getVersion())) {
            Context.revert("Invalid DID Message Version.");
        }

        didMessage.setHashedMessage(Base64.getDecoder().decode(hashedMessage.getBytes()));

        return didMessage;
    }
}
