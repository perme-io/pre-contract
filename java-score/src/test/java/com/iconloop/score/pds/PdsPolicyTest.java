package com.iconloop.score.pds;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import foundation.icon.icx.crypto.ECDSASignature;
import foundation.icon.icx.data.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import score.Address;
import score.UserRevertedException;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;


class DidSignature {
    public String message;
    public byte[] signature;

    public DidSignature(String message, byte[] recoverableSerialize) {
        this.message = message;
        this.signature = recoverableSerialize;
    }
}


public class PdsPolicyTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private Account[] owners;
    private String[] owners_did;
    private ECKeyPair[] owners_keyPair;
    private Score pdsPolicyScore;
    private PdsPolicy pdsPolicySpy;
    private Score didSummaryScore;


    @BeforeEach
    void setup() throws Exception {
        // setup accounts and deploy
        owners = new Account[3];
        owners_did = new String[3];
        owners_keyPair = new ECKeyPair[3];

        for (int i = 0; i < owners.length; i++) {
            owners[i] = sm.createAccount(100);
            owners_did[i] = "did:icon:01:" + i;

            BigInteger privKey = new BigInteger("a" + i, 16);
            BigInteger pubKey = Sign.publicKeyFromPrivate(privKey);
            owners_keyPair[i] = new ECKeyPair(privKey, pubKey);
        }

        pdsPolicyScore = sm.deploy(owners[0], PdsPolicy.class);
        didSummaryScore = sm.deploy(owners[0], DidSummaryMock.class);

        // setup spy
        pdsPolicySpy = (PdsPolicy) spy(pdsPolicyScore.getInstance());
        pdsPolicyScore.setInstance(pdsPolicySpy);

        // setup External Score
        Address didSummaryScore_ = didSummaryScore.getAddress();
        pdsPolicyScore.invoke(owners[0], "set_did_summary_score", didSummaryScore_);

        // Add public key to DID summary Contract
        for (int i = 0; i < owners.length; i++) {
            DidSignature owners_sign = makeSignature(owners_did[i], owners_keyPair[i], "publicKey", "target", 0);
            didSummaryScore.invoke(owners[0], "addPublicKey", owners_sign.message, "publicKey", owners_sign.signature);
        }
    }

//    private DidSignature2 makeSignature2(String did, ECKeyPair keyPair, String kid, String target, int nonce) {
//        String message = did + "#" + kid + "#" + target + "#" + nonce;
//        byte[] msgHash = Hash.sha3(message.getBytes());
//        byte[] signMsg = Hash.sha3(msgHash);
//
//        Sign.SignatureData signature = Sign.signMessage(signMsg, keyPair, false);
//
//        System.out.println("\nmessage in TEST: " + message);
//        BigInteger pubKeyRecovered = Sign.signedMessageToKey(msgHash, signature);
//        System.out.println("Recovered in TEST: " + pubKeyRecovered.toString(16));
//
//        return new DidSignature2(message, signature);
//    }

    private DidSignature makeSignature(String did, ECKeyPair keyPair, String kid, String target, int nonce) {
        String message = did + "#" + kid + "#" + target + "#" + nonce;
        byte[] msgHash = Hash.sha3(message.getBytes());
        byte[] signMsg = Hash.sha3(msgHash);

        ECDSASignature signature = new ECDSASignature(new Bytes(keyPair.getPrivateKey()));
        BigInteger[] sig = signature.generateSignature(msgHash);
        byte[] recoverableSerialize = signature.recoverableSerialize(sig, msgHash);

        return new DidSignature(message, recoverableSerialize);
    }

    @Test
    void makeSignature() {
        DidSignature sign = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "target", 0);
        DidSignature sign2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey2", "target2", 0);
        DidSignature sign3 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey3", "target3", 0);

        didSummaryScore.invoke(owners[0], "addPublicKey", sign.message, "publicKey", sign.signature);
        didSummaryScore.invoke(owners[0], "addPublicKey", sign2.message, "publicKey2", sign2.signature);
        didSummaryScore.invoke(owners[0], "addPublicKey", sign3.message, "publicKey3", sign3.signature);

        var key1 = (String) didSummaryScore.call("getPublicKey", owners_did[0], "publicKey");
        var key2 = (String) didSummaryScore.call("getPublicKey", owners_did[0], "publicKey2");
        var key3 = (String) didSummaryScore.call("getPublicKey", owners_did[0], "publicKey3");

        assertTrue(key1.equals(key2) && key1.equals(key3));
    }

    @Test
    void addLabel() {
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_A000", "TEST_LABEL_A", null, null, owners[0].getAddress().toString(), "", "");
        verify(pdsPolicySpy).PDSEvent(EventType.AddLabel.name(), "TEST_LABEL_A000", owners[0].getAddress().toString(), 0);
    }

    @Test
    void addLabelByDid() {
        System.out.println("addLabelByDid");
        DidSignature sign = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_A000", 0);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_A000", "TEST_LABEL_A", sign.message, sign.signature, owners_did[0], "", "");
        verify(pdsPolicySpy).PDSEvent(EventType.AddLabel.name(), "TEST_LABEL_A000", owners_did[0], 0);
    }

    @Test
    void preventUpdateLabelByOthers() {
        DidSignature sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_TO_UPDATE", 0);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_TO_UPDATE", "TEST_LABEL_A", sign_1.message, sign_1.signature, owners_did[0], "", "");

        DidSignature sign_2 = makeSignature(owners_did[0], owners_keyPair[1], "publicKey", "TEST_LABEL_TO_UPDATE", 0);
        assertThrows(UserRevertedException.class, () ->
                pdsPolicyScore.invoke(owners[0], "update_label","TEST_LABEL_TO_UPDATE", sign_1.message, sign_2.signature, "TEST_LABEL_UPDATED", null, null, null));
    }

    @Test
    void removeLabel() {
        DidSignature sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_TO_REMOVE", 0);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_TO_REMOVE", "TEST_LABEL_A", sign_1.message, sign_1.signature, owners_did[0], "", "");

        // prev signature is not valid.
        assertThrows(UserRevertedException.class, () ->
                pdsPolicyScore.invoke(owners[0], "remove_label","TEST_LABEL_TO_REMOVE", sign_1.message, sign_1.signature));

        // need new signature to remove.
        DidSignature sign_2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_TO_REMOVE", 1);
        pdsPolicyScore.invoke(owners[0], "remove_label","TEST_LABEL_TO_REMOVE", sign_2.message, sign_2.signature);
        verify(pdsPolicySpy).PDSEvent(EventType.RemoveLabel.name(), "TEST_LABEL_TO_REMOVE", owners_did[0], 0);
    }

    @Test
    void updateLabel() {
        DidSignature sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_TO_UPDATE", 0);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_TO_UPDATE", "TEST_LABEL_A", sign_1.message, sign_1.signature, owners_did[0], "", "");

        DidSignature sign_2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_TO_UPDATE", 1);
        pdsPolicyScore.invoke(owners[0], "update_label","TEST_LABEL_TO_UPDATE", sign_2.message, sign_2.signature, "TEST_LABEL_UPDATED", null, null, null);
        verify(pdsPolicySpy).PDSEvent(EventType.UpdateLabel.name(), "TEST_LABEL_TO_UPDATE", owners_did[0], 1);
    }

    @Test
    void preventUpdateLabelBySameSign() {
        DidSignature sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_TO_UPDATE_A", 0);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_TO_UPDATE_A", "TEST_LABEL_A", sign_1.message, sign_1.signature, owners_did[0], "", "");

        DidSignature sign_2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_TO_UPDATE_B", 0);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_TO_UPDATE_B", "TEST_LABEL_B", sign_2.message, sign_2.signature, owners_did[0], "", "");

        DidSignature sign_3 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_TO_UPDATE_A", 1);
        pdsPolicyScore.invoke(owners[0], "update_label","TEST_LABEL_TO_UPDATE_A", sign_3.message, sign_3.signature, "TEST_LABEL_UPDATED", null, null, null);
        verify(pdsPolicySpy).PDSEvent(EventType.UpdateLabel.name(), "TEST_LABEL_TO_UPDATE_A", owners_did[0], 1);

        // need new signature with next nonce.
        DidSignature sign_4 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_TO_UPDATE_A", 2);
        pdsPolicyScore.invoke(owners[0], "update_label","TEST_LABEL_TO_UPDATE_A", sign_4.message, sign_4.signature, "TEST_LABEL_UPDATED", null, null, null);
        verify(pdsPolicySpy).PDSEvent(EventType.UpdateLabel.name(), "TEST_LABEL_TO_UPDATE_A", owners_did[0], 1);

        // need new signature for another label.
        DidSignature sign_5 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_TO_UPDATE_B", 1);
        pdsPolicyScore.invoke(owners[0], "update_label","TEST_LABEL_TO_UPDATE_B", sign_5.message, sign_5.signature, "TEST_LABEL_UPDATED", null, null, null);
        verify(pdsPolicySpy).PDSEvent(EventType.UpdateLabel.name(), "TEST_LABEL_TO_UPDATE_B", owners_did[0], 1);
    }

    @Test
    void updateData() {
        DidSignature sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_TO_UPDATE", 0);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_TO_UPDATE", "TEST_LABEL_A", sign_1.message, sign_1.signature, owners_did[0], "", "");

        DidSignature sign_2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_TO_UPDATE", 1);
        pdsPolicyScore.invoke(owners[0], "update_data","TEST_LABEL_TO_UPDATE", "TEST_DATA", sign_2.message, sign_2.signature, null);
        verify(pdsPolicySpy).PDSEvent(EventType.UpdateData.name(), "TEST_LABEL_TO_UPDATE", owners_did[0], 1);
    }

    @Test
    void getLabel() {
        DidSignature sign = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_TO_GET", 0);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_TO_GET", "TEST_LABEL_A", sign.message, sign.signature, owners_did[0], "", "");

        var label = (Map<String, Object>) pdsPolicyScore.call("get_label", "TEST_LABEL_TO_GET");
        assertEquals("TEST_LABEL_A", label.get("name"));
    }

    @Test
    void addPolicy() {
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_FOR_POLICY", "TEST_LABEL_A", null, null, owners[0].getAddress().toString(), "", "");

        pdsPolicyScore.invoke(owners[0], "add_policy","TEST_POLICY_A000", "TEST_LABEL_FOR_POLICY", "TEST_POLICY_A", owners[0].getAddress().toString(), 3, 5,  null, null, null, null);
        verify(pdsPolicySpy).PDSEvent(EventType.AddPolicy.name(), "TEST_POLICY_A000", owners[0].getAddress().toString(), 0);
    }

    @Test
    void addPolicyByDid() {
        DidSignature sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_FOR_POLICY", 0);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_FOR_POLICY", "TEST_LABEL_A", sign_1.message, sign_1.signature, owners_did[0], "", "");

        DidSignature sign_2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_POLICY_A000", 0);
        pdsPolicyScore.invoke(owners[0], "add_policy","TEST_POLICY_A000", "TEST_LABEL_FOR_POLICY", "TEST_POLICY_A", owners_did[0], 3, 5, sign_2.message, sign_2.signature, null, null);
        verify(pdsPolicySpy).PDSEvent(EventType.AddPolicy.name(), "TEST_POLICY_A000", owners_did[0], 0);
    }

    @Test
    void removePolicy() {
        DidSignature sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_FOR_POLICY", 0);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_FOR_POLICY", "TEST_LABEL_A", sign_1.message, sign_1.signature, owners_did[0], "", "");

        DidSignature sign_2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_POLICY_TO_REMOVE", 0);
        pdsPolicyScore.invoke(owners[0], "add_policy","TEST_POLICY_TO_REMOVE", "TEST_LABEL_FOR_POLICY", "TEST_POLICY_A", owners_did[0], 3, 5, sign_2.message, sign_2.signature, null, null);

        DidSignature sign_3 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_POLICY_TO_REMOVE", 1);
        pdsPolicyScore.invoke(owners[0], "remove_policy","TEST_POLICY_TO_REMOVE", sign_3.message, sign_3.signature);
        verify(pdsPolicySpy).PDSEvent(EventType.RemovePolicy.name(), "TEST_POLICY_TO_REMOVE", owners_did[0], 0);
    }

    @Test
    void checkPolicy() {
        DidSignature sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_FOR_POLICY", 0);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_FOR_POLICY", "TEST_LABEL_A", sign_1.message, sign_1.signature, owners_did[0], "", "");

        DidSignature sign_2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_POLICY_A000", 0);
        pdsPolicyScore.invoke(owners[0], "add_policy","TEST_POLICY_A000", "TEST_LABEL_FOR_POLICY", "TEST_POLICY_A", owners_did[0], 3, 5, sign_2.message, sign_2.signature, null, null);

        var policy_checked = (Map<String, Object>) pdsPolicyScore.call("check_policy","TEST_POLICY_A000", owners_did[0], owners_did[0]);
        assertEquals(true, policy_checked.get("checked"));

        var policy_checked_owner_only = (Map<String, Object>) pdsPolicyScore.call("check_policy","TEST_POLICY_A000", owners_did[0], null);
        assertEquals(true, policy_checked_owner_only.get("checked"));

        var policy_checked_consumer_only = (Map<String, Object>) pdsPolicyScore.call("check_policy","TEST_POLICY_A000", null, owners_did[0]);
        assertEquals(true, policy_checked_consumer_only.get("checked"));

        var policy_checked_null = (Map<String, Object>) pdsPolicyScore.call("check_policy","TEST_POLICY_A000", null, null);
        assertEquals(false, policy_checked_null.get("checked"));
    }

    @Test
    void getPolicy() {
        DidSignature sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_LABEL_FOR_POLICY", 0);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_FOR_POLICY", "TEST_LABEL_A", sign_1.message, sign_1.signature, owners_did[0], "", "");

        DidSignature sign_2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", "TEST_POLICY_A000", 0);
        pdsPolicyScore.invoke(owners[0], "add_policy","TEST_POLICY_A000", "TEST_LABEL_FOR_POLICY", "TEST_POLICY_A", owners_did[0], 3, 5, sign_2.message, sign_2.signature, null, null);

        var policy = (Map<String, Object>) pdsPolicyScore.call("get_policy","TEST_POLICY_A000");
        assertEquals("TEST_POLICY_A", policy.get("name"));
    }

    @Test
    void addNode() {
        pdsPolicyScore.invoke(owners[0], "add_node","TEST_NODE_A000", "111.222.333.1", null, null, null);
        verify(pdsPolicySpy).PDSEvent(EventType.AddNode.name(), "TEST_NODE_A000", "111.222.333.1", 0);
    }

    @Test
    void removeNode() {
        pdsPolicyScore.invoke(owners[0], "add_node","TEST_NODE_TO_REMOVE", "111.222.333.1", null, null, null);

        pdsPolicyScore.invoke(owners[0], "remove_node","TEST_NODE_TO_REMOVE");
        verify(pdsPolicySpy).PDSEvent(EventType.RemoveNode.name(), "TEST_NODE_TO_REMOVE", "111.222.333.1", 0);
    }

    @Test
    void updateNode() {
        pdsPolicyScore.invoke(owners[0], "add_node","TEST_NODE_TO_UPDATE", "111.222.333.1", null, null, null);

        pdsPolicyScore.invoke(owners[0], "update_node","TEST_NODE_TO_UPDATE", "111.222.333.2", null, null, null);
        verify(pdsPolicySpy).PDSEvent(EventType.UpdateNode.name(), "TEST_NODE_TO_UPDATE", "111.222.333.2", 0);
    }

    @Test
    void getNode() {
        pdsPolicyScore.invoke(owners[0], "add_node","TEST_NODE_TO_GET", "111.222.333.1", null, null, null);

        var node = (Map<String, Object>) pdsPolicyScore.call("get_node","TEST_NODE_TO_GET");
        assertEquals("TEST_NODE_TO_GET", node.get("peer_id"));
        assertEquals("111.222.333.1", node.get("endpoint"));
    }

    @Test
    void getDidSummaryScore() {
        Address didSummaryScore_ = Address.fromString("cx0d0d6336c1b5ce40b57792991a5b9eb2b84be9bf");
        pdsPolicyScore.invoke(owners[0], "set_did_summary_score", didSummaryScore_);

        var didSummaryScore = (Address) pdsPolicyScore.call("get_did_summary_score");
        assertEquals(didSummaryScore_.toString(), didSummaryScore.toString());
    }
}
