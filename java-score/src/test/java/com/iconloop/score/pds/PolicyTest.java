package com.iconloop.score.pds;

import com.iconloop.score.pds.util.Jwt;
import com.iconloop.score.test.Account;
import com.iconloop.score.test.Score;
import com.iconloop.score.test.ServiceManager;
import com.iconloop.score.test.TestBase;
import com.parametacorp.jwt.Payload;
import com.parametacorp.util.Converter;
import foundation.icon.did.core.Algorithm;
import foundation.icon.did.core.AlgorithmProvider;
import foundation.icon.did.core.DidKeyHolder;
import foundation.icon.did.exceptions.AlgorithmException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import score.UserRevertedException;
import score.impl.Crypto;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class PolicyTest extends TestBase {
    private static final BigInteger ONE_DAY = BigInteger.valueOf(60 * 60 * 24 * 1_000_000L);
    private static final ServiceManager sm = getServiceManager();
    private static final Account owner = sm.createAccount();
    private static final Algorithm algorithm = AlgorithmProvider.create(AlgorithmProvider.Type.ES256K);
    private static Score didScore;
    private static Score policyScore;
    private static DidKeyHolder key1;
    private static DidKeyHolder key2;

    private final Random rand = new Random();

    @BeforeAll
    static void beforeAll() throws Exception {
        didScore = sm.deploy(owner, DidScoreMock.class);
        policyScore = sm.deploy(owner, PdsPolicy.class, didScore.getAddress());
        key1 = createDidAndKeyHolder("key1");
        key2 = createDidAndKeyHolder("key2");
    }

    private static DidKeyHolder createDidAndKeyHolder(String kid) throws AlgorithmException {
        var keyProvider = algorithm.generateKeyProvider(kid);
        var pubkey = algorithm.publicKeyToByte(keyProvider.getPublicKey());
        var did = createDummyDid(pubkey);
        didScore.invoke(owner, "register", did, kid, pubkey);
        return new DidKeyHolder.Builder(keyProvider)
                .did(did)
                .build();
    }

    private static String createDummyDid(byte[] seeds) {
        byte[] msgHash = Crypto.hash("sha3-256", seeds);
        byte[] buf = new byte[24];
        System.arraycopy(msgHash, 0, buf, 0, buf.length);
        return "did:icon:03:" + Converter.bytesToHex(buf);
    }

    private String getRandomLabelId() {
        return "label_" + rand.nextInt(10000);
    }

    private Object[] getParams(DidKeyHolder keyHolder, String method, String labelId) throws AlgorithmException {
        Payload payload = new Payload.Builder(method)
                .labelId(labelId)
                .build();
        Jwt jwt = new Jwt.Builder(keyHolder.getKid())
                .payload(payload)
                .build();
        var timestamp = BigInteger.valueOf(sm.getBlock().getTimestamp());
        switch (method) {
            case "add_label":
                return new Object[] {
                        labelId, "name_" + labelId, "<kid>#<publicKey>", timestamp.add(ONE_DAY), jwt.sign(keyHolder),
                        // Optional
                        null, null, BigInteger.ZERO, null, BigInteger.ZERO
                };
            case "remove_label":
                return new Object[] {
                        labelId, jwt.sign(keyHolder)
                };
        }
        throw new IllegalArgumentException("Invalid method: " + method);
    }

    private Object[] getParams(DidKeyHolder keyHolder, String method, String labelId, long lastUpdated) throws AlgorithmException {
        Payload payload = new Payload.Builder(method)
                .labelId(labelId)
                .baseHeight(lastUpdated)
                .build();
        Jwt jwt = new Jwt.Builder(keyHolder.getKid())
                .payload(payload)
                .build();
        switch (method) {
            case "update_label":
                return new Object[] {
                        labelId, jwt.sign(keyHolder),
                        // Optional
                        null, BigInteger.ZERO, "newCategory", null, BigInteger.ZERO
                };
        }
        throw new IllegalArgumentException("Invalid method: " + method);
    }

    @Test
    void labelTest() throws Exception {
        // add label
        var labelId = getRandomLabelId();
        policyScore.invoke(owner, "add_label", getParams(key1, "add_label", labelId));
        var label = (LabelInfo) policyScore.call("get_label", labelId);
        System.out.println(label);
        assertEquals(BigInteger.ONE, policyScore.call(BigInteger.class, "get_label_count"));

        // Negative: try to add with the same labelId
        assertThrows(UserRevertedException.class, () ->
                policyScore.invoke(owner, "add_label", getParams(key1, "add_label", labelId)));

        // update label
        policyScore.invoke(owner, "update_label", getParams(key1, "update_label", labelId, label.getLast_updated()));
        label = (LabelInfo) policyScore.call("get_label", labelId);
        System.out.println(label);

        // Negative: try to update with an invalid baseHeight
        final long invalidBaseHeight = label.getLast_updated() - 1;
        assertThrows(UserRevertedException.class, () ->
                policyScore.invoke(owner, "update_label", getParams(key1, "update_label", labelId, invalidBaseHeight)));
        final long invalidBaseHeight2 = sm.getBlock().getHeight() + 1;
        assertThrows(UserRevertedException.class, () ->
                policyScore.invoke(owner, "update_label", getParams(key1, "update_label", labelId, invalidBaseHeight2)));

        // remove label
        policyScore.invoke(owner, "remove_label", getParams(key1, "remove_label", labelId));
        label = (LabelInfo) policyScore.call("get_label", labelId);
        System.out.println(label);
        assertTrue(label.isRevoked());
        assertEquals(BigInteger.ZERO, policyScore.call(BigInteger.class, "get_label_count"));
    }
}
