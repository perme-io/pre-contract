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
            DidMessage owners_sign = makeSignature(owners_did[i], owners_keyPair[i], "publicKey", owners[i].getAddress(), "", "", BigInteger.ZERO);
            didSummaryScore.invoke(owners[i], "addPublicKey", owners_sign.getMessage(), owners_sign.getSignature());
        }
    }

    private DidMessage makeSignature(String did, ECKeyPair keyPair, String kid, Address from, String target, String method, BigInteger lastUpdated) {
        DidMessage message = new DidMessage(did, kid, from, target, method, lastUpdated);
        byte[] msgHash = Hash.sha3(message.getMessageForHash());
        message.setHashedMessage(msgHash);

        ECDSASignature signature = new ECDSASignature(new Bytes(keyPair.getPrivateKey()));
        BigInteger[] sig = signature.generateSignature(msgHash);
        message.setSignature(signature.recoverableSerialize(sig, msgHash));

        return message;
    }

    @Test
    void makeSignature() {
        DidMessage sign = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "", "", BigInteger.ZERO);
        DidMessage sign2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey2", owners[0].getAddress(), "", "", BigInteger.ZERO);
        DidMessage sign3 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey3", owners[0].getAddress(), "", "", BigInteger.ZERO);

        didSummaryScore.invoke(owners[0], "addPublicKey", sign.getMessage(), sign.getSignature());
        didSummaryScore.invoke(owners[0], "addPublicKey", sign2.getMessage(), sign2.getSignature());
        didSummaryScore.invoke(owners[0], "addPublicKey", sign3.getMessage(), sign3.getSignature());

        var key1 = (String) didSummaryScore.call("getPublicKey", owners_did[0], "publicKey");
        var key2 = (String) didSummaryScore.call("getPublicKey", owners_did[0], "publicKey2");
        var key3 = (String) didSummaryScore.call("getPublicKey", owners_did[0], "publicKey3");

        assertTrue(key1.equals(key2) && key1.equals(key3));
    }

    @Test
    void addLabel() {
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_A000", "TEST_LABEL_A", null, null, owners[0].getAddress().toString(), "", "");

        var labelInfo = (Map<String, Object>) pdsPolicyScore.call("get_label", "TEST_LABEL_A000");
        verify(pdsPolicySpy).PDSEvent(EventType.AddLabel.name(), "TEST_LABEL_A000", owners[0].getAddress().toString(), (BigInteger) labelInfo.get("last_updated"));
    }

    @Test
    void addLabelByDid() {
        System.out.println("addLabelByDid");
        DidMessage sign = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL_A000", "add_label", BigInteger.ZERO);
        pdsPolicyScore.invoke(owners[0], "add_label", "TEST_LABEL_A000", "TEST_LABEL_A", sign.getMessage(), sign.getSignature(), owners_did[0], "", "");

        var labelInfo = (Map<String, Object>) pdsPolicyScore.call("get_label", "TEST_LABEL_A000");
        verify(pdsPolicySpy).PDSEvent(EventType.AddLabel.name(), "TEST_LABEL_A000", owners_did[0], (BigInteger) labelInfo.get("last_updated"));
    }

    @Test
    void preventUpdateLabelByOthers() {
        DidMessage sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL_TO_UPDATE", "add_label", BigInteger.ZERO);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_TO_UPDATE", "TEST_LABEL_A", sign_1.getMessage(), sign_1.getSignature(), owners_did[0], "", "");

        DidMessage sign_2 = makeSignature(owners_did[0], owners_keyPair[1], "publicKey", owners[0].getAddress(), "TEST_LABEL_TO_UPDATE", "update_label", BigInteger.ZERO);
        assertThrows(UserRevertedException.class, () ->
                pdsPolicyScore.invoke(owners[0], "update_label","TEST_LABEL_TO_UPDATE", sign_1.getMessage(), sign_2.getSignature(), "TEST_LABEL_UPDATED", null, null, null));
    }

    @Test
    void removeLabel() {
        DidMessage sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL_TO_REMOVE", "add_label", BigInteger.ZERO);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_TO_REMOVE", "TEST_LABEL_A", sign_1.getMessage(), sign_1.getSignature(), owners_did[0], "", "");

        // prev signature is not valid.
        assertThrows(UserRevertedException.class, () ->
                pdsPolicyScore.invoke(owners[0], "remove_label","TEST_LABEL_TO_REMOVE", sign_1.getMessage(), sign_1.getSignature()));

        // need new signature to remove.
        var label = (Map<String, Object>) pdsPolicyScore.call("get_label", "TEST_LABEL_TO_REMOVE");
        DidMessage sign_2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL_TO_REMOVE", "remove_label", (BigInteger) label.get("last_updated"));
        pdsPolicyScore.invoke(owners[0], "remove_label","TEST_LABEL_TO_REMOVE", sign_2.getMessage(), sign_2.getSignature());
        verify(pdsPolicySpy).PDSEvent(EventType.RemoveLabel.name(), "TEST_LABEL_TO_REMOVE", owners_did[0], (BigInteger) label.get("last_updated"));
    }

    @Test
    void updateLabel() {
        DidMessage sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL_TO_UPDATE", "add_label", BigInteger.ZERO);
        pdsPolicyScore.invoke(owners[0], "add_label", "TEST_LABEL_TO_UPDATE", "TEST_LABEL_A", sign_1.getMessage(), sign_1.getSignature(), owners_did[0], "", "");

        var label = (Map<String, Object>) pdsPolicyScore.call("get_label", "TEST_LABEL_TO_UPDATE");
        DidMessage sign_2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL_TO_UPDATE", "update_label", (BigInteger) label.get("last_updated"));
        pdsPolicyScore.invoke(owners[0], "update_label","TEST_LABEL_TO_UPDATE", sign_2.getMessage(), sign_2.getSignature(), "TEST_LABEL_UPDATED", null, null, null);

        label = (Map<String, Object>) pdsPolicyScore.call("get_label", "TEST_LABEL_TO_UPDATE");
        verify(pdsPolicySpy).PDSEvent(EventType.UpdateLabel.name(), "TEST_LABEL_TO_UPDATE", owners_did[0], (BigInteger) label.get("last_updated"));
    }

    @Test
    void preventUpdateLabelBySameSign() {
        DidMessage sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL___A___", "add_label", BigInteger.ZERO);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL___A___", "TEST_LABEL_A", sign_1.getMessage(), sign_1.getSignature(), owners_did[0], "", "");

        DidMessage sign_2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL___B___", "add_label", BigInteger.ZERO);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL___B___", "TEST_LABEL_B", sign_2.getMessage(), sign_2.getSignature(), owners_did[0], "", "");

        var label = (Map<String, Object>) pdsPolicyScore.call("get_label", "TEST_LABEL___A___");
        BigInteger prev_updated = (BigInteger) label.get("last_updated");
        DidMessage sign_3 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL___A___", "update_label", (BigInteger) label.get("last_updated"));
        System.out.println("last_updated: " + label.get("last_updated"));

        pdsPolicyScore.invoke(owners[0], "update_label","TEST_LABEL___A___", sign_3.getMessage(), sign_3.getSignature(), "UPDATE___1___", null, null, null);
        label = (Map<String, Object>) pdsPolicyScore.call("get_label", "TEST_LABEL___A___");
        assertEquals("TEST_LABEL___A___", label.get("label_id"));
        assertEquals("UPDATE___1___", label.get("name"));
        assert(prev_updated.compareTo((BigInteger) label.get("last_updated")) < 0);
        System.out.println("last_updated: " + label.get("last_updated"));

        // need new signature with prev last_updated.
        DidMessage sign_4 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL___A___", "update_label", (BigInteger) label.get("last_updated"));
        pdsPolicyScore.invoke(owners[0], "update_label","TEST_LABEL___A___", sign_4.getMessage(), sign_4.getSignature(), "UPDATE___2___", null, null, null);
        prev_updated = (BigInteger) label.get("last_updated");
        label = (Map<String, Object>) pdsPolicyScore.call("get_label", "TEST_LABEL___A___");
        assertEquals("TEST_LABEL___A___", label.get("label_id"));
        assertEquals("UPDATE___2___", label.get("name"));
        assert(prev_updated.compareTo((BigInteger) label.get("last_updated")) < 0);

        // need new signature for another label.
        label = (Map<String, Object>) pdsPolicyScore.call("get_label", "TEST_LABEL___B___");
        prev_updated = (BigInteger) label.get("last_updated");
        DidMessage sign_5 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL___B___", "update_label", (BigInteger) label.get("last_updated"));
        pdsPolicyScore.invoke(owners[0], "update_label","TEST_LABEL___B___", sign_5.getMessage(), sign_5.getSignature(), "UPDATE___1___", null, null, null);
        label = (Map<String, Object>) pdsPolicyScore.call("get_label", "TEST_LABEL___B___");
        assertEquals("TEST_LABEL___B___", label.get("label_id"));
        assertEquals("UPDATE___1___", label.get("name"));
        assert(prev_updated.compareTo((BigInteger) label.get("last_updated")) < 0);
    }

    @Test
    void updateData() {
        DidMessage sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL_TO_UPDATE", "add_label", BigInteger.ZERO);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_TO_UPDATE", "TEST_LABEL_A", sign_1.getMessage(), sign_1.getSignature(), owners_did[0], "", "");

        var label = (Map<String, Object>) pdsPolicyScore.call("get_label", "TEST_LABEL_TO_UPDATE");
        DidMessage sign_2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL_TO_UPDATE", "update_data", (BigInteger) label.get("last_updated"));
        pdsPolicyScore.invoke(owners[0], "update_data","TEST_LABEL_TO_UPDATE", "TEST_DATA", sign_2.getMessage(), sign_2.getSignature(), null);

        label = (Map<String, Object>) pdsPolicyScore.call("get_label", "TEST_LABEL_TO_UPDATE");
        verify(pdsPolicySpy).PDSEvent(EventType.UpdateData.name(), "TEST_LABEL_TO_UPDATE", owners_did[0], (BigInteger) label.get("last_updated"));
    }

    @Test
    void getLabel() {
        DidMessage sign = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL_TO_GET", "add_label", BigInteger.ZERO);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_TO_GET", "TEST_LABEL_A", sign.getMessage(), sign.getSignature(), owners_did[0], "", "");

        var label = (Map<String, Object>) pdsPolicyScore.call("get_label", "TEST_LABEL_TO_GET");
        assertEquals("TEST_LABEL_A", label.get("name"));
    }

    @Test
    void addPolicy() {
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_FOR_POLICY", "TEST_LABEL_A", null, null, owners[0].getAddress().toString(), "", "");

        pdsPolicyScore.invoke(owners[0], "add_policy","TEST_POLICY_A000", "TEST_LABEL_FOR_POLICY", "TEST_POLICY_A", owners[0].getAddress().toString(), 3, 5,  null, null, null, null);

        var policy = (Map<String, Object>) pdsPolicyScore.call("get_policy","TEST_POLICY_A000");
        verify(pdsPolicySpy).PDSEvent(EventType.AddPolicy.name(), "TEST_POLICY_A000", owners[0].getAddress().toString(), (BigInteger) policy.get("last_updated"));
    }

    @Test
    void addPolicyByDid() {
        DidMessage sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL_FOR_POLICY", "add_label", BigInteger.ZERO);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_FOR_POLICY", "TEST_LABEL_A", sign_1.getMessage(), sign_1.getSignature(), owners_did[0], "", "");

        DidMessage sign_2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_POLICY_A000", "add_policy", BigInteger.ZERO);
        pdsPolicyScore.invoke(owners[0], "add_policy","TEST_POLICY_A000", "TEST_LABEL_FOR_POLICY", "TEST_POLICY_A", owners_did[0], 3, 5, sign_2.getMessage(), sign_2.getSignature(), null, null);

        var policy = (Map<String, Object>) pdsPolicyScore.call("get_policy","TEST_POLICY_A000");
        verify(pdsPolicySpy).PDSEvent(EventType.AddPolicy.name(), "TEST_POLICY_A000", owners_did[0], (BigInteger) policy.get("last_updated"));
    }

    @Test
    void removePolicy() {
        DidMessage sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL_FOR_POLICY", "add_label", BigInteger.ZERO);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_FOR_POLICY", "TEST_LABEL_A", sign_1.getMessage(), sign_1.getSignature(), owners_did[0], "", "");

        DidMessage sign_2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_POLICY_TO_REMOVE", "add_policy", BigInteger.ZERO);
        pdsPolicyScore.invoke(owners[0], "add_policy","TEST_POLICY_TO_REMOVE", "TEST_LABEL_FOR_POLICY", "TEST_POLICY_A", owners_did[0], 3, 5, sign_2.getMessage(), sign_2.getSignature(), null, null);

        var policyInfo = (Map<String, Object>) pdsPolicyScore.call("get_policy","TEST_POLICY_TO_REMOVE", null, owners_did[0]);
        DidMessage sign_3 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_POLICY_TO_REMOVE", "remove_policy", (BigInteger) policyInfo.get("last_updated"));
        pdsPolicyScore.invoke(owners[0], "remove_policy","TEST_POLICY_TO_REMOVE", sign_3.getMessage(), sign_3.getSignature());
        verify(pdsPolicySpy).PDSEvent(EventType.RemovePolicy.name(), "TEST_POLICY_TO_REMOVE", owners_did[0], (BigInteger) policyInfo.get("last_updated"));
    }

    @Test
    void checkPolicy() {
        DidMessage sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL_FOR_POLICY", "add_label", BigInteger.ZERO);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_FOR_POLICY", "TEST_LABEL_A", sign_1.getMessage(), sign_1.getSignature(), owners_did[0], "", "");

        DidMessage sign_2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_POLICY_A000", "add_policy", BigInteger.ZERO);
        pdsPolicyScore.invoke(owners[0], "add_policy","TEST_POLICY_A000", "TEST_LABEL_FOR_POLICY", "TEST_POLICY_A", owners_did[0], 3, 5, sign_2.getMessage(), sign_2.getSignature(), null, null);

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
        DidMessage sign_1 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_LABEL_FOR_POLICY", "add_label", BigInteger.ZERO);
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_FOR_POLICY", "TEST_LABEL_A", sign_1.getMessage(), sign_1.getSignature(), owners_did[0], "", "");

        DidMessage sign_2 = makeSignature(owners_did[0], owners_keyPair[0], "publicKey", owners[0].getAddress(), "TEST_POLICY_A000", "add_policy", BigInteger.ZERO);
        pdsPolicyScore.invoke(owners[0], "add_policy","TEST_POLICY_A000", "TEST_LABEL_FOR_POLICY", "TEST_POLICY_A", owners_did[0], 3, 5, sign_2.getMessage(), sign_2.getSignature(), null, null);

        var policy = (Map<String, Object>) pdsPolicyScore.call("get_policy","TEST_POLICY_A000");
        assertEquals("TEST_POLICY_A", policy.get("name"));
    }

    @Test
    void addNode() {
        pdsPolicyScore.invoke(owners[0], "add_node","TEST_NODE_A000", "111.222.333.1", null, null, null);
        verify(pdsPolicySpy).PDSEvent(EventType.AddNode.name(), "TEST_NODE_A000", "111.222.333.1", BigInteger.ZERO);
    }

    @Test
    void removeNode() {
        pdsPolicyScore.invoke(owners[0], "add_node","TEST_NODE_TO_REMOVE", "111.222.333.1", null, null, null);

        pdsPolicyScore.invoke(owners[0], "remove_node","TEST_NODE_TO_REMOVE");
        verify(pdsPolicySpy).PDSEvent(EventType.RemoveNode.name(), "TEST_NODE_TO_REMOVE", "111.222.333.1", BigInteger.ZERO);
    }

    @Test
    void updateNode() {
        pdsPolicyScore.invoke(owners[0], "add_node","TEST_NODE_TO_UPDATE", "111.222.333.1", null, null, null);

        pdsPolicyScore.invoke(owners[0], "update_node","TEST_NODE_TO_UPDATE", "111.222.333.2", null, null, null);
        verify(pdsPolicySpy).PDSEvent(EventType.UpdateNode.name(), "TEST_NODE_TO_UPDATE", "111.222.333.2", BigInteger.ZERO);
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
