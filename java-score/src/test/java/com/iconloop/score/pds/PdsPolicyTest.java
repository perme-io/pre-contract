package com.iconloop.score.pds;

import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;

import java.math.BigInteger;

import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;


public class PdsPolicyTest extends TestBase {
    private static final ServiceManager sm = getServiceManager();
    private Account[] owners;
    private Score pdsPolicyScore;
    private PdsPolicy pdsPolicySpy;

    private String owner_did;
    private byte[] did_sign;

    @BeforeEach
    void setup() throws Exception {
        // setup accounts and deploy
        owners = new Account[3];
        for (int i = 0; i < owners.length; i++) {
            owners[i] = sm.createAccount(100);
        }

        pdsPolicyScore = sm.deploy(owners[0], PdsPolicy.class);

        // setup spy
        pdsPolicySpy = (PdsPolicy) spy(pdsPolicyScore.getInstance());
        pdsPolicyScore.setInstance(pdsPolicySpy);

        // setup for owner did
        owner_did = "did:icon:01:765949249b90e78641c51840c7a6a7d4a3383d9c8a6327bd";

        BigInteger privKey = new BigInteger("97ddae0f3a25b92268175400149d65d6887b9cefaf28ea2c078e05cdc15a3c0a", 16);
        BigInteger pubKey = Sign.publicKeyFromPrivate(privKey);
        ECKeyPair keyPair = new ECKeyPair(privKey, pubKey);

        byte[] didHash_as_msg = Hash.sha3(owner_did.getBytes());
        Sign.SignatureData signature = Sign.signMessage(didHash_as_msg, keyPair, false);
        did_sign = new byte[65];
        System.arraycopy(signature.getR(), 0, did_sign, 0, 32);
    }

    @Test
    void addLabel() {
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_A000", "TEST_LABEL_A", null, null, owners[0].getAddress().toString(), "", "");
        verify(pdsPolicySpy).PDSEvent(EventType.AddLabel.name(), "TEST_LABEL_A000", owners[0].getAddress().toString());
    }

    @Test
    void addLabelByDid() {
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_A000", "TEST_LABEL_A", owner_did, did_sign, owner_did, "", "");
        verify(pdsPolicySpy).PDSEvent(EventType.AddLabel.name(), "TEST_LABEL_A000", owner_did);
    }

    @Test
    void removeLabel() {
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_TO_REMOVE", "TEST_LABEL_A", owner_did, did_sign, owner_did, "", "");

        pdsPolicyScore.invoke(owners[0], "remove_label","TEST_LABEL_TO_REMOVE", owner_did, did_sign);
        verify(pdsPolicySpy).PDSEvent(EventType.RemoveLabel.name(), "TEST_LABEL_TO_REMOVE", owner_did);
    }

    @Test
    void updateLabel() {
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_TO_UPDATE", "TEST_LABEL_A", owner_did, did_sign, owner_did, "", "");

        pdsPolicyScore.invoke(owners[0], "update_label","TEST_LABEL_TO_UPDATE", owner_did, did_sign, "TEST_LABEL_UPDATED", null, null, null);
        verify(pdsPolicySpy).PDSEvent(EventType.UpdateLabel.name(), "TEST_LABEL_TO_UPDATE", owner_did);
    }

    @Test
    void updateData() {
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_TO_UPDATE", "TEST_LABEL_A", owner_did, did_sign, owner_did, "", "");

        pdsPolicyScore.invoke(owners[0], "update_data","TEST_LABEL_TO_UPDATE", "TEST_DATA", owner_did, did_sign, null);
        verify(pdsPolicySpy).PDSEvent(EventType.UpdateData.name(), "TEST_LABEL_TO_UPDATE", owner_did);
    }

    @Test
    void addPolicy() {
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_FOR_POLICY", "TEST_LABEL_A", null, null, owners[0].getAddress().toString(), "", "");

        pdsPolicyScore.invoke(owners[0], "add_policy","TEST_POLICY_A000", "TEST_LABEL_FOR_POLICY", "TEST_POLICY_A", owners[0].getAddress().toString(), 3, 5,  null, null, null, null);
        verify(pdsPolicySpy).PDSEvent(EventType.AddPolicy.name(), "TEST_POLICY_A000", owners[0].getAddress().toString());
    }

    @Test
    void addPolicyByDid() {
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_FOR_POLICY", "TEST_LABEL_A", owner_did, did_sign, owner_did, "", "");

        pdsPolicyScore.invoke(owners[0], "add_policy","TEST_POLICY_A000", "TEST_LABEL_FOR_POLICY", "TEST_POLICY_A", owner_did, 3, 5, owner_did, did_sign, null, null);
        verify(pdsPolicySpy).PDSEvent(EventType.AddPolicy.name(), "TEST_POLICY_A000", owner_did);
    }

    @Test
    void removePolicy() {
        pdsPolicyScore.invoke(owners[0], "add_label","TEST_LABEL_FOR_POLICY", "TEST_LABEL_A", owner_did, did_sign, owner_did, "", "");

        pdsPolicyScore.invoke(owners[0], "add_policy","TEST_POLICY_TO_REMOVE", "TEST_LABEL_FOR_POLICY", "TEST_POLICY_A", owner_did, 3, 5, owner_did, did_sign, null, null);

        pdsPolicyScore.invoke(owners[0], "remove_policy","TEST_POLICY_TO_REMOVE", owner_did, did_sign);
        verify(pdsPolicySpy).PDSEvent(EventType.RemovePolicy.name(), "TEST_POLICY_TO_REMOVE", owner_did);
    }
}
