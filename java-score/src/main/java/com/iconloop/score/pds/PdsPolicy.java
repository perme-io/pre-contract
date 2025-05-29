package com.iconloop.score.pds;

import com.parametacorp.jwt.Payload;
import com.parametacorp.util.Converter;
import com.parametacorp.util.EnumerableMap;
import score.Address;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import scorex.util.StringTokenizer;

import java.math.BigInteger;
import java.util.Map;

public class PdsPolicy implements Label, Policy, Node {
    private static final BigInteger ONE_ICX = new BigInteger("1000000000000000000");

    private final DictDB<String, LabelInfo> labelInfos = Context.newDictDB("labelInfos", LabelInfo.class);
    private final DictDB<String, PolicyInfo> policyInfos = Context.newDictDB("policyInfos", PolicyInfo.class);
    private final EnumerableMap<String, NodeInfo> nodeInfos = new EnumerableMap<>("nodeInfos", String.class, NodeInfo.class);
    private final VarDB<BigInteger> labelCount = Context.newVarDB("labelCount", BigInteger.class);
    private final VarDB<BigInteger> policyCount = Context.newVarDB("policyCount", BigInteger.class);
    private final VarDB<BigInteger> minStakeForServe = Context.newVarDB("minStakeForServe", BigInteger.class);
    private final VarDB<BigInteger> systemThreshold = Context.newVarDB("systemThreshold", BigInteger.class);
    private final VarDB<Address> didScore = Context.newVarDB("didScore", Address.class);
    private final VarDB<Address> bfsScore = Context.newVarDB("bfsScore", Address.class);

    public PdsPolicy(Address did_score, Address bfs_score) {
        this.didScore.set(did_score);
        this.bfsScore.set(bfs_score);
    }

    @External(readonly=true)
    public Address get_did_score() {
        return this.didScore.get();
    }

    @External(readonly=true)
    public Address get_bfs_score() {
        return this.bfsScore.get();
    }

    private void onlyOwner() {
        Context.require(Context.getCaller().equals(Context.getOwner()), "Only owner can call this method.");
    }

    @External
    public void set_min_stake_value(BigInteger min_stake_for_serve) {
        onlyOwner();
        Context.require(min_stake_for_serve.signum() > 0, "min_stake should be greater than 0");
        this.minStakeForServe.set(min_stake_for_serve);
    }

    @External(readonly=true)
    public BigInteger get_min_stake_value() {
        return this.minStakeForServe.getOrDefault(BigInteger.ZERO);
    }

    @External
    public void set_system_threshold(BigInteger threshold) {
        onlyOwner();
        Context.require(threshold.signum() > 0, "threshold should be greater than 0");
        this.systemThreshold.set(threshold);
    }

    @External(readonly=true)
    public BigInteger get_system_threshold() {
        return this.systemThreshold.getOrDefault(BigInteger.ONE);
    }

    private void validateThreshold(BigInteger threshold) {
        Context.require(threshold.equals(get_system_threshold()), "threshold should be equal to the system threshold");
    }

    @External(readonly=true)
    public LabelInfo get_label(String label_id) {
        return this.labelInfos.get(label_id);
    }

    private LabelInfo checkLabelId(String label_id) {
        LabelInfo labelInfo = get_label(label_id);
        Context.require(labelInfo != null, "invalid label_id");
        Context.require(!labelInfo.isRevoked(), "label_id is revoked");
        return labelInfo;
    }

    private String validateDid(String did) {
        String[] tokens = new String[4];
        StringTokenizer tokenizer = new StringTokenizer(did, ":");
        for (int i = 0; i < tokens.length; i++) {
            Context.require(tokenizer.hasMoreTokens(), "validateDid: need more tokens");
            tokens[i] = tokenizer.nextToken();
        }
        Context.require(!tokenizer.hasMoreTokens(), "validateDid: should be no more tokens");
        if ("did".equals(tokens[0]) && "icon".equals(tokens[1])) {
            try {
                byte[] nid = Converter.hexToBytes(tokens[2]);
                byte[] idAndChecksum = Converter.hexToBytes(tokens[3]);
                if (idAndChecksum.length == 24) {
                    return did;
                }
            } catch (IllegalArgumentException ignored) {}
        }
        Context.revert("invalid did");
        return null;
    }

    private byte[] getConsumerPubkey(String consumer) {
        // <did#kid>
        String[] tokens = new String[2];
        StringTokenizer tokenizer = new StringTokenizer(consumer, "#");
        for (int i = 0; i < tokens.length; i++) {
            Context.require(tokenizer.hasMoreTokens(), "validateConsumer: need more tokens");
            tokens[i] = tokenizer.nextToken();
        }
        Context.require(!tokenizer.hasMoreTokens(), "validateConsumer: should be no more tokens");
        var did = validateDid(tokens[0]);
        var kid = tokens[1];

        byte[] pubKey = Context.call(byte[].class, get_did_score(), "getPublicKey", did, kid);
        Context.require(pubKey != null, "cannot find public key for " + did + "#" + kid);
        return pubKey;
    }

    private String verifySignature(String signature, Payload expected) {
        var sigChecker = new SignatureChecker();
        Context.require(sigChecker.verifySig(get_did_score(), signature), "failed to verify signature");
        Context.require(sigChecker.validatePayload(expected), "failed to validate payload");
        return sigChecker.getOwnerId();
    }

    private void validateExpireAt(BigInteger expireAt) {
        var blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        Context.require(expireAt.compareTo(blockTimestamp) > 0, "label or producer has expired");
    }

    @External
    public void add_label(String label_id,
                          String name,
                          String public_key,
                          BigInteger expire_at,
                          String owner_sign,
                          @Optional String category,
                          @Optional String producer,
                          @Optional BigInteger producer_expire_at,
                          @Optional String data_id,
                          @Optional BigInteger data_size) {
        Context.require(!label_id.isEmpty(), "label_id is empty");
        Context.require(get_label(label_id) == null, "label_id already exists");

        String ownerId = verifySignature(owner_sign, new Payload.Builder("add_label")
                .labelId(label_id)
                .build());

        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        Context.require(expire_at.compareTo(blockTimestamp) > 0, "expire_at must be greater than blockTimestamp");

        String producerId = (producer == null) ? ownerId : validateDid(producer);
        BigInteger producerExpireAt = (producer_expire_at.signum() == 0) ? expire_at : producer_expire_at;
        Context.require(producerExpireAt.compareTo(blockTimestamp) > 0, "producer_expire_at must be greater than blockTimestamp");
        Context.require(producerExpireAt.compareTo(expire_at) <= 0, "producer_expire_at must be less than equal to expire_at");

        var labelInfo = new LabelInfo.Builder()
                .labelId(label_id)
                .owner(ownerId)
                .name(name)
                .publicKey(public_key)
                .expireAt(expire_at)
                .category(category)
                .producer(producerId)
                .producerExpireAt(producerExpireAt)
                .created(Context.getBlockHeight())
                .build();
        this.labelInfos.set(label_id, labelInfo);
        LabelAdded(label_id, ownerId, producerId);

        BigInteger total = get_label_count();
        this.labelCount.set(total.add(BigInteger.ONE));

        // add data if provided
        if (data_id != null && data_size.signum() > 0) {
            addData(data_id, name, data_size, labelInfo);
        }
    }

    @External
    public void remove_label(String label_id,
                             String owner_sign) {
        var labelInfo = checkLabelId(label_id);

        String ownerId = verifySignature(owner_sign, new Payload.Builder("remove_label")
                .labelId(label_id)
                .build());
        labelInfo.checkOwnerOrThrow(ownerId);

        // remove all data and policies associated with this label
        var dataSize = labelInfo.removeDataAll();
        var policySize = labelInfo.removePolicyAll(policyInfos);
        this.policyCount.set(get_policy_count().subtract(BigInteger.valueOf(policySize)));

        labelInfo.revoke(Context.getBlockHeight());
        this.labelInfos.set(label_id, labelInfo);
        LabelRemoved(label_id);
        this.labelCount.set(get_label_count().subtract(BigInteger.ONE));

        // revoke the group in bfs_score to unpin data
        if (dataSize > 0) {
            updateGroup(labelInfo.getLabel_id(), BigInteger.ONE);
        }
    }

    @External
    public void update_label(String label_id,
                             String owner_sign,
                             @Optional String name,
                             @Optional BigInteger expire_at,
                             @Optional String category,
                             @Optional String producer,
                             @Optional BigInteger producer_expire_at) {
        var labelInfo = checkLabelId(label_id);

        String ownerId = verifySignature(owner_sign, new Payload.Builder("update_label")
                .labelId(label_id)
                .baseHeight(labelInfo.getLast_updated())
                .build());
        labelInfo.checkOwnerOrThrow(ownerId);

        // check label expiration
        var labelExpireAt = labelInfo.getExpire_at();
        validateExpireAt(labelExpireAt);

        var attrs = new LabelInfo.Builder();
        if (name != null) {
            attrs.name(name);
        }
        var expireAtUpdated = (expire_at.signum() > 0);
        if (expireAtUpdated) {
            attrs.expireAt(expire_at);
            labelExpireAt = expire_at;
        }
        if (category != null) {
            attrs.category(category);
        }
        if (producer != null) {
            attrs.producer(validateDid(producer));
        }
        if (producer_expire_at.signum() > 0) {
            Context.require(producer_expire_at.compareTo(labelExpireAt) <= 0, "producer_expire_at must be less than equal to expire_at");
            attrs.producerExpireAt(producer_expire_at);
        }
        attrs.lastUpdated(Context.getBlockHeight());

        labelInfo.update(attrs);
        this.labelInfos.set(label_id, labelInfo);
        LabelUpdated(label_id);

        if (expireAtUpdated) {
            updateGroup(labelInfo.getLabel_id(), labelExpireAt);
        }
    }

    @External
    public void add_data(String label_id,
                         String data_id,
                         String name,
                         BigInteger size,
                         String producer_sign) {
        var labelInfo = checkLabelId(label_id);

        String producer = verifySignature(producer_sign, new Payload.Builder("add_data")
                .labelId(label_id)
                .dataId(data_id)
                .build());
        Context.require(labelInfo.getProducer().equals(producer), "unauthorized producer");

        // check producer_expire_at
        validateExpireAt(labelInfo.getProducer_expire_at());

        addData(data_id, name, size, labelInfo);
    }

    private void addData(String dataId, String name, BigInteger size, LabelInfo labelInfo) {
        var dataInfo = new DataInfo(dataId, name, size);
        Context.require(labelInfo.addData(dataInfo), "data already exists");
        LabelData(labelInfo.getLabel_id(), dataId);

        // pin data by calling bfs_score
        Context.call(get_bfs_score(), "pin",
                dataId, size, labelInfo.getExpire_at(), labelInfo.getLabel_id(), name);
    }

    private void updateGroup(String labelId, BigInteger expireAt) {
        // update group expires at bfs_score
        Context.call(get_bfs_score(), "update_group", labelId, expireAt);
    }

    @External(readonly=true)
    public DataInfo get_data(String label_id, String data_id) {
        var labelInfo = checkLabelId(label_id);
        return labelInfo.getData(data_id);
    }

    @External(readonly=true)
    public PageOfData get_data_list(String label_id,
                                    int offset,
                                    @Optional int limit) {
        var labelInfo = checkLabelId(label_id);
        return labelInfo.getDataPage(offset, limit);
    }

    @External(readonly=true)
    public PolicyInfo get_policy(String policy_id) {
        return this.policyInfos.get(policy_id);
    }

    private PolicyInfo checkPolicyId(String policy_id) {
        PolicyInfo policyInfo = get_policy(policy_id);
        Context.require(policyInfo != null, "invalid policy_id");
        return policyInfo;
    }

    private String createPolicyId(String labelId, byte[] consumerPubkey) {
        // Keccak-256(label_id + consumer_pubkey)[0:16]
        var labelBytes = labelId.getBytes();
        byte[] msgBytes = new byte[labelBytes.length + consumerPubkey.length];
        System.arraycopy(labelBytes, 0, msgBytes, 0, labelBytes.length);
        System.arraycopy(consumerPubkey, 0, msgBytes, labelBytes.length, consumerPubkey.length);
        return Converter.bytesToHex(Context.hash("keccak-256", msgBytes), 0, 16);
    }

    private void validatePolicyId(String policyId, String labelId, byte[] consumerPubkey) {
        var expected = createPolicyId(labelId, consumerPubkey);
        Context.require(expected.equals(policyId), "invalid policy_id, expected=" + expected);
    }

    @External
    public void add_policy(String policy_id,
                           String label_id,
                           String name,
                           String consumer,
                           BigInteger threshold,
                           String owner_sign,
                           @Optional BigInteger expire_at) {
        Context.require(!policy_id.isEmpty(), "policy_id is empty");
        Context.require(get_policy(policy_id) == null, "policy_id already exists");
        LabelInfo labelInfo = checkLabelId(label_id);
        validatePolicyId(policy_id, label_id, getConsumerPubkey(consumer));
        validateThreshold(threshold);

        String ownerId = verifySignature(owner_sign, new Payload.Builder("add_policy")
                .labelId(label_id)
                .policyId(policy_id)
                .build());
        labelInfo.checkOwnerOrThrow(ownerId);

        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger expireAt = (expire_at.signum() == 0) ? labelInfo.getExpire_at() : expire_at;
        Context.require(expireAt.compareTo(blockTimestamp) > 0, "expire_at must be greater than blockTimestamp");
        Context.require(expireAt.compareTo(labelInfo.getExpire_at()) <= 0, "expire_at must be less than equal to the label's expire_at");

        var policyInfo = new PolicyInfo.Builder()
                .policyId(policy_id)
                .labelId(label_id)
                .name(name)
                .consumer(consumer)
                .threshold(threshold)
                .expireAt(expireAt)
                .created(Context.getBlockHeight())
                .build();

        labelInfo.addPolicyId(policy_id);
        this.policyInfos.set(policy_id, policyInfo);
        PolicyAdded(policy_id, label_id, consumer);

        BigInteger total = get_policy_count();
        this.policyCount.set(total.add(BigInteger.ONE));
    }

    @External
    public void update_policy(String policy_id,
                              BigInteger expire_at,
                              String owner_sign) {
        PolicyInfo policyInfo = checkPolicyId(policy_id);
        LabelInfo labelInfo = checkLabelId(policyInfo.getLabel_id());

        String ownerId = verifySignature(owner_sign, new Payload.Builder("update_policy")
                .policyId(policy_id)
                .baseHeight(policyInfo.getLast_updated())
                .build());
        labelInfo.checkOwnerOrThrow(ownerId);

        // new expire_at can be any value within the label's expire_at.
        // setting the new expire_at to zero means the policy will expire immediately.
        Context.require(expire_at.compareTo(labelInfo.getExpire_at()) <= 0, "expire_at must be less than equal to the label's expire_at");

        var attrs = new PolicyInfo.Builder()
                .expireAt(expire_at);
        attrs.lastUpdated(Context.getBlockHeight());

        policyInfo.update(attrs);
        this.policyInfos.set(policy_id, policyInfo);
        PolicyUpdated(policy_id);
    }

    @External(readonly=true)
    public Map<String, Object> check_policy(String policy_id) {
        PolicyInfo policyInfo = checkPolicyId(policy_id);
        LabelInfo labelInfo = checkLabelId(policyInfo.getLabel_id());
        boolean checked = false;

        BigInteger labelExpireAt = labelInfo.getExpire_at();
        BigInteger policyExpireAt = policyInfo.getExpire_at();
        BigInteger current = BigInteger.valueOf(Context.getBlockTimestamp());
        if (current.compareTo(policyExpireAt) < 0 && current.compareTo(labelExpireAt) < 0) {
            // not expired: valid policy
            checked = true;
        }

        return Map.ofEntries(
                Map.entry("owner", labelInfo.getOwner()),
                Map.entry("policy_id", policy_id),
                Map.entry("label_id", labelInfo.getLabel_id()),
                Map.entry("checked", checked),
                Map.entry("expire_at", policyExpireAt),
                Map.entry("label_expire_at", labelExpireAt)
        );
    }

    @External(readonly=true)
    public PageOfPolicy get_policy_list(String label_id,
                                        int offset,
                                        @Optional int limit) {
        var labelInfo = checkLabelId(label_id);
        return labelInfo.getPoliciesPage(policyInfos, offset, limit);
    }

    @External(readonly=true)
    public NodeInfo get_node(String peer_id) {
        return this.nodeInfos.get(peer_id);
    }

    private NodeInfo checkPeerId(String peer_id) {
        NodeInfo nodeInfo = get_node(peer_id);
        Context.require(nodeInfo != null, "invalid peer_id");
        Context.require(nodeInfo.checkOwner(Context.getCaller()), "invalid owner");
        return nodeInfo;
    }

    @External
    @Payable
    public void add_node(String peer_id,
                         String name,
                         String endpoint,
                         @Optional Address owner) {
        Context.require(!peer_id.isEmpty(), "peer_id is empty");
        Context.require(this.nodeInfos.get(peer_id) == null, "peer_id already exists");

        BigInteger stake = Context.getValue();
        BigInteger minStake = get_min_stake_value();
        Context.require(stake.compareTo(ONE_ICX.multiply(minStake)) >= 0, "needs at least " + minStake + " ICX to add a node");

        Address ownerAddress = (owner == null) ? Context.getCaller() : owner;
        NodeInfo nodeInfo = new NodeInfo(peer_id, name, endpoint, ownerAddress, Context.getBlockHeight(), stake, BigInteger.ZERO);
        this.nodeInfos.set(peer_id, nodeInfo);
        NodeAdded(peer_id, ownerAddress, endpoint);
    }

    @External
    public void remove_node(String peer_id) {
        checkPeerId(peer_id);
        this.nodeInfos.remove(peer_id);
        NodeRemoved(peer_id);
    }

    @External
    @Payable
    public void update_node(String peer_id,
                            @Optional Address owner,
                            @Optional String name,
                            @Optional String endpoint) {
        NodeInfo nodeInfo = checkPeerId(peer_id);

        BigInteger stake = nodeInfo.getStake();
        if (Context.getValue().signum() > 0) {
            stake = stake.add(Context.getValue());
        }
        BigInteger minStake = get_min_stake_value();
        Context.require(stake.compareTo(ONE_ICX.multiply(minStake)) >= 0, "needs at least " + minStake + " ICX to update a node");

        Address ownerAddress = (owner == null) ? Context.getCaller() : owner;
        nodeInfo.update(name, endpoint, ownerAddress, stake, BigInteger.ZERO);
        this.nodeInfos.set(peer_id, nodeInfo);
        NodeUpdated(peer_id, ownerAddress, endpoint);
    }

    @External(readonly=true)
    public NodeInfo[] all_nodes() {
        NodeInfo[] allNode = new NodeInfo[nodeInfos.length()];
        for (int i = 0; i < allNode.length; i++) {
            var key = nodeInfos.getKey(i);
            allNode[i] = nodeInfos.get(key);
        }
        return allNode;
    }

    @External(readonly=true)
    public BigInteger get_label_count() {
        return this.labelCount.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly=true)
    public BigInteger get_policy_count() {
        return this.policyCount.getOrDefault(BigInteger.ZERO);
    }

    @EventLog(indexed=3)
    public void LabelAdded(String label_id, String owner, String producer) {}

    @EventLog(indexed=1)
    public void LabelRemoved(String label_id) {}

    @EventLog(indexed=1)
    public void LabelUpdated(String label_id) {}

    @EventLog(indexed=2)
    public void LabelData(String label_id, String data_id) {}

    @EventLog(indexed=3)
    public void PolicyAdded(String policy_id, String label_id, String consumer) {}

    @EventLog(indexed=1)
    public void PolicyUpdated(String policy_id) {}

    @EventLog(indexed=1)
    public void NodeAdded(String peer_id, Address owner, String endpoint) {}

    @EventLog(indexed=1)
    public void NodeUpdated(String peer_id, Address owner, String endpoint) {}

    @EventLog(indexed=1)
    public void NodeRemoved(String peer_id) {}
}
