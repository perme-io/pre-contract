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
import java.util.Map;
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

    public static class ParamsBuilder {
        private final DidKeyHolder signer;
        private final String method;
        private String labelId;
        private String policyId;
        private long baseHeight;
        private DidKeyHolder producer;
        private DidKeyHolder consumer;
        private String dataId;
        private String category;
        private BigInteger expireAt;

        public ParamsBuilder(DidKeyHolder signer, String method) {
            this.signer = signer;
            this.method = method;
        }

        public ParamsBuilder labelId(String labelId) {
            this.labelId = labelId;
            return this;
        }

        public ParamsBuilder policyId(String policyId) {
            this.policyId = policyId;
            return this;
        }

        public ParamsBuilder baseHeight(long baseHeight) {
            this.baseHeight = baseHeight;
            return this;
        }

        public ParamsBuilder producer(DidKeyHolder producer) {
            this.producer = producer;
            return this;
        }

        public ParamsBuilder consumer(DidKeyHolder consumer) {
            this.consumer = consumer;
            return this;
        }

        public ParamsBuilder dataId(String dataId) {
            this.dataId = dataId;
            return this;
        }

        public ParamsBuilder category(String category) {
            this.category = category;
            return this;
        }

        public ParamsBuilder expireAt(BigInteger expireAt) {
            this.expireAt = expireAt;
            return this;
        }

        public Object[] build() throws AlgorithmException {
            var pb = new Payload.Builder(method);
            if (labelId != null) {
                pb.labelId(labelId);
            }
            if (dataId != null) {
                pb.dataId(dataId);
            }
            if (policyId != null) {
                pb.policyId(policyId);
            }
            if (baseHeight > 0) {
                pb.baseHeight(baseHeight);
            }
            Jwt jwt = new Jwt.Builder(signer.getKid())
                    .payload(pb.build())
                    .build();
            var signature = jwt.sign(signer);
            var timestamp = BigInteger.valueOf(sm.getBlock().getTimestamp());
            switch (method) {
                case "add_label":
                    return new Object[] {
                            labelId, "name_" + labelId, "<kid>#<publicKey>", timestamp.add(ONE_DAY), signature,
                            // Optional
                            null, null, BigInteger.ZERO, null, BigInteger.ZERO
                    };
                case "remove_label":
                    return new Object[] {
                            labelId, signature,
                    };
                case "update_label":
                    return new Object[] {
                            labelId, signature,
                            // Optional
                            null, BigInteger.ZERO, (category != null) ? category : null,
                            (producer != null) ? producer.getDid() : null, BigInteger.ZERO
                    };
                case "add_data":
                    return new Object[] {
                            labelId, dataId, "name_" + dataId, BigInteger.valueOf(1000), signature,
                    };
                case "add_policy":
                    return new Object[] {
                            policyId, labelId, "name_" + policyId,
                            (consumer != null) ? consumer.getDid() : null, BigInteger.TWO, signature,
                            // Optional
                            BigInteger.ZERO
                    };
                case "update_policy":
                    return new Object[] {
                            policyId, expireAt, signature,
                    };
            }
            throw new IllegalArgumentException("Invalid method: " + method);
        }
    }

    private String addRandomLabel(DidKeyHolder keyHolder) throws AlgorithmException {
        var labelId = "label_" + rand.nextInt(10000);
        policyScore.invoke(owner, "add_label", new ParamsBuilder(keyHolder, "add_label").labelId(labelId).build());
        return labelId;
    }

    private void removeLabel(DidKeyHolder keyHolder, String labelId) throws AlgorithmException {
        policyScore.invoke(owner, "remove_label", new ParamsBuilder(keyHolder, "remove_label").labelId(labelId).build());
    }

    @Test
    void labelTest() throws Exception {
        // add label
        var labelId = addRandomLabel(key1);
        var label = (LabelInfo) policyScore.call("get_label", labelId);
        System.out.println(label);
        assertEquals(BigInteger.ONE, policyScore.call(BigInteger.class, "get_label_count"));

        // Negative: try to add with the same labelId
        assertThrows(UserRevertedException.class, () ->
                policyScore.invoke(owner, "add_label", new ParamsBuilder(key1, "add_label").labelId(labelId).build()));

        // update label
        policyScore.invoke(owner, "update_label",
                new ParamsBuilder(key1, "update_label").labelId(labelId)
                        .category("newCategory")
                        .baseHeight(label.getLast_updated()).build());
        label = (LabelInfo) policyScore.call("get_label", labelId);
        assertEquals("newCategory", label.getCategory());
        System.out.println(label);

        // Negative: try to update with an invalid baseHeight
        final long invalidBaseHeight = label.getLast_updated() - 1;
        assertThrows(UserRevertedException.class, () -> policyScore.invoke(owner, "update_label",
                new ParamsBuilder(key1, "update_label").labelId(labelId).baseHeight(invalidBaseHeight).build()));
        final long invalidBaseHeight2 = sm.getBlock().getHeight() + 1;
        assertThrows(UserRevertedException.class, () -> policyScore.invoke(owner, "update_label",
                new ParamsBuilder(key1, "update_label").labelId(labelId).baseHeight(invalidBaseHeight2).build()));

        // remove label
        removeLabel(key1, labelId);
        label = (LabelInfo) policyScore.call("get_label", labelId);
        System.out.println(label);
        assertTrue(label.isRevoked());
        assertEquals(BigInteger.ZERO, policyScore.call(BigInteger.class, "get_label_count"));
    }

    @Test
    void addDataTest() throws Exception {
        // add label
        String labelId = addRandomLabel(key1);
        var label = (LabelInfo) policyScore.call("get_label", labelId);
        System.out.println(label);

        // Negative: try to add data with the unauthorized producer
        String cid = "data_" + rand.nextInt(10000);
        assertThrows(UserRevertedException.class, () ->
                policyScore.invoke(owner, "add_data", new ParamsBuilder(key2, "add_data").labelId(labelId).dataId(cid).build()));

        // update producer to key2
        policyScore.invoke(owner, "update_label",
                new ParamsBuilder(key1, "update_label").labelId(labelId)
                        .baseHeight(label.getLast_updated())
                        .producer(key2).build());

        // now add_data should succeed
        policyScore.invoke(owner, "add_data", new ParamsBuilder(key2, "add_data").labelId(labelId).dataId(cid).build());

        // Negative: try to add data with the same cid
        assertThrows(UserRevertedException.class, () ->
                policyScore.invoke(owner, "add_data", new ParamsBuilder(key2, "add_data").labelId(labelId).dataId(cid).build()));

        // add more data
        for (int i = 0; i < 30; i++) {
            var dataId = "data_test" + i;
            policyScore.invoke(owner, "add_data", new ParamsBuilder(key2, "add_data").labelId(labelId).dataId(dataId).build());
        }

        var page = (PageOfData) policyScore.call("get_data", labelId, 0, 0);
        assertEquals(0, page.getOffset());
        assertEquals(25, page.getSize());
        assertEquals(31, page.getTotal());
        assertEquals(25, page.getIds().length);

        var page2 = (PageOfData) policyScore.call("get_data", labelId, -11, 20);
        assertEquals(20, page2.getOffset());
        assertEquals(11, page2.getSize());
        assertEquals(31, page2.getTotal());
        assertEquals(11, page2.getIds().length);

        // cleanup: remove label
        removeLabel(key1, labelId);
    }

    @Test
    void policyTest() throws Exception {
        // add label
        String labelId = addRandomLabel(key1);
        var label = (LabelInfo) policyScore.call("get_label", labelId);
        System.out.println(label);

        var policyId = "policy_" + rand.nextInt(10000);
        policyScore.invoke(owner, "add_policy",
                new ParamsBuilder(key1, "add_policy").labelId(labelId)
                        .policyId(policyId).consumer(key2).build());
        var policy = (PolicyInfo) policyScore.call("get_policy", policyId);
        System.out.println(policy);
        assertEquals(key2.getDid(), policy.getConsumer());
        assertEquals(label.getExpire_at(), policy.getExpire_at());
        assertEquals(BigInteger.ONE, policyScore.call(BigInteger.class, "get_policy_count"));

        // ensure check_policy returns true
        var checkPolicy = (Map) policyScore.call("check_policy", policyId);
        System.out.println(checkPolicy);
        assertTrue((Boolean) checkPolicy.get("checked"));

        // Negative: try to add with the same policyId
        assertThrows(UserRevertedException.class, () ->
                policyScore.invoke(owner, "add_policy",
                        new ParamsBuilder(key1, "add_policy").labelId(labelId)
                                .policyId(policyId).consumer(key2).build()));

        // update policy: revoke the policy
        policyScore.invoke(owner, "update_policy",
                new ParamsBuilder(key1, "update_policy").policyId(policyId)
                        .baseHeight(policy.getLast_updated())
                        .expireAt(BigInteger.ZERO).build());
        policy = (PolicyInfo) policyScore.call("get_policy", policyId);
        System.out.println(policy);
        assertEquals(BigInteger.ZERO, policy.getExpire_at());

        // ensure check_policy returns false
        checkPolicy = (Map) policyScore.call("check_policy", policyId);
        System.out.println(checkPolicy);
        assertFalse((Boolean) checkPolicy.get("checked"));

        // Negative: try to update with an invalid expireAt
        BigInteger invalidExpireAt = label.getExpire_at().add(BigInteger.ONE);
        var baseHeight = policy.getLast_updated();
        assertThrows(UserRevertedException.class, () ->
                policyScore.invoke(owner, "update_policy",
                        new ParamsBuilder(key1, "update_policy").policyId(policyId)
                                .baseHeight(baseHeight)
                                .expireAt(invalidExpireAt).build()));

        // add more policies
        for (int i = 0; i < 30; i++) {
            var pid = "policy_test" + i;
            policyScore.invoke(owner, "add_policy",
                    new ParamsBuilder(key1, "add_policy").labelId(labelId)
                            .policyId(pid).consumer(key2).build());
        }
        assertEquals(BigInteger.valueOf(31), policyScore.call(BigInteger.class, "get_policy_count"));

        var page = (PageOfPolicy) policyScore.call("get_policies", labelId, 0, 0);
        assertEquals(0, page.getOffset());
        assertEquals(25, page.getSize());
        assertEquals(31, page.getTotal());
        assertEquals(25, page.getIds().length);

        var page2 = (PageOfPolicy) policyScore.call("get_policies", labelId, -1, 1);
        assertEquals(30, page2.getOffset());
        assertEquals(1, page2.getSize());
        assertEquals(31, page2.getTotal());
        assertEquals(1, page2.getIds().length);

        // cleanup: remove label
        removeLabel(key1, labelId);
    }
}
