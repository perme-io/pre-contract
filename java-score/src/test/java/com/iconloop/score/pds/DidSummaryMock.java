package com.iconloop.score.pds;

import score.Address;
import score.ArrayDB;
import score.Context;
import score.DictDB;
import score.annotation.EventLog;
import score.annotation.External;

import java.math.BigInteger;


enum SummaryEventType {
    AddPublicKey
}


public class DidSummaryMock {
    private final ArrayDB<Address> admins = Context.newArrayDB("admins", Address.class);
    private final DictDB<String, DidInfo> didInfos;

    public DidSummaryMock() {
        this.didInfos = Context.newDictDB("didInfos", DidInfo.class);
    }

    @External
    public void addPublicKey(String did_msg, byte[] did_sign) {
        // TODO remove kid parameter.
        DidMessage message = DidMessage.parse(did_msg);
        byte[] recoveredKey = Context.recoverKey("ecdsa-secp256k1", message.getHashedMessage(), did_sign, false);
        String publicKey = new BigInteger(recoveredKey).toString(16);

//        System.out.println("Recovered in SCORE: " + publicKey);
//        System.out.println("didMessage.did: " + message.did);

        DidInfo didInfo = this.didInfos.get(message.did);
        if (didInfo == null) {
            didInfo = new DidInfo(message.did, null, null);
        }

        didInfo.addPublicKey(message.kid, publicKey);
        this.didInfos.set(message.did, didInfo);

        DIDSummaryEvent(SummaryEventType.AddPublicKey.name(), message.did, message.kid);
    }

    @External(readonly=true)
    public String getPublicKey(String did, String kid) {
        DidInfo didInfo = this.didInfos.get(did);
        Context.require(didInfo != null, "Invalid request target(did).");

        return didInfo.getPublicKey(kid);
    }

    /*
     * Events
     */
    @EventLog
    protected void DIDSummaryEvent(String event, String value1, String value2) {}
}
