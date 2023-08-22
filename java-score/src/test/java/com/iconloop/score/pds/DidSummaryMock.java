package com.iconloop.score.pds;

import score.Address;
import score.ArrayDB;
import score.Context;
import score.DictDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Payable;

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
    public void addPublicKey(String did_msg, String kid, byte[] did_sign) {
        DidMessage didMessage = Helper.DidMessageParser(did_msg);

        byte[] msgHash = Context.hash("keccak-256", did_msg.getBytes());
//        System.out.println("message in SCORE: " + did_msg);
        byte[] recoveredKey = Context.recoverKey("ecdsa-secp256k1", msgHash, did_sign, false);
        String publicKey = new BigInteger(recoveredKey).toString(16);

//        System.out.println("Recovered in SCORE: " + publicKey);
//        System.out.println("didMessage.did: " + didMessage.did);

        DidInfo didInfo = this.didInfos.get(didMessage.did);
        if (didInfo == null) {
            didInfo = new DidInfo(didMessage.did, null, null);
        }

        didInfo.addPublicKey(kid, publicKey);
        this.didInfos.set(didMessage.did, didInfo);

        DIDSummaryEvent(SummaryEventType.AddPublicKey.name(), didMessage.did, kid);
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
