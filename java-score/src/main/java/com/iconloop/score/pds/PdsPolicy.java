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
    private final VarDB<Address> didScore = Context.newVarDB("didScore", Address.class);

    public PdsPolicy(Address did_score) {
        this.didScore.set(did_score);
    }

    @External(readonly=true)
    public Address get_did_score() {
        return this.didScore.get();
    }

    @External
    public void set_min_stake_value(BigInteger min_stake_for_serve) {
        Context.require(Context.getCaller().equals(Context.getOwner()), "Only owner can call this method.");
        this.minStakeForServe.set(min_stake_for_serve);
    }

    @External(readonly=true)
    public BigInteger get_min_stake_value() {
        return this.minStakeForServe.getOrDefault(BigInteger.ZERO);
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

    private void verifySignature(SignatureChecker sigChecker, String signature) {
        Context.require(sigChecker.verifySig(get_did_score(), signature), "failed to verify signature");
    }

    private void validatePayload(SignatureChecker sigChecker, Payload expected) {
        Context.require(sigChecker.validatePayload(expected), "failed to validate payload");
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
                          @Optional String data,
                          @Optional BigInteger data_size) {
        Context.require(!label_id.isEmpty(), "label_id is empty");
        Context.require(get_label(label_id) == null, "label_id already exists");

        var sigChecker = new SignatureChecker();
        verifySignature(sigChecker, owner_sign);
        validatePayload(sigChecker, new Payload.Builder("add_label")
                .labelId(label_id)
                .build());

        String ownerId = sigChecker.getOwnerId();
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
        if (data != null && data_size.signum() > 0) {
            addData(data, name, data_size, labelInfo);
        }
    }

    @External
    public void remove_label(String label_id,
                             String owner_sign) {
        var labelInfo = checkLabelId(label_id);

        var sigChecker = new SignatureChecker();
        verifySignature(sigChecker, owner_sign);
        validatePayload(sigChecker, new Payload.Builder("remove_label")
                .labelId(label_id)
                .build());

        String ownerId = sigChecker.getOwnerId();
        Context.require(labelInfo.checkOwner(ownerId), "You do not have permission.");

        // remove all data and policies associated with this label
        labelInfo.removeDataAll();
        var size = labelInfo.removePolicyAll(policyInfos);
        this.policyCount.set(get_policy_count().subtract(BigInteger.valueOf(size)));

        labelInfo.revoke(Context.getBlockHeight());
        this.labelInfos.set(label_id, labelInfo);
        LabelRemoved(label_id);
        this.labelCount.set(get_label_count().subtract(BigInteger.ONE));
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

        var sigChecker = new SignatureChecker();
        verifySignature(sigChecker, owner_sign);
        validatePayload(sigChecker, new Payload.Builder("update_label")
                .labelId(label_id)
                .baseHeight(labelInfo.getLast_updated())
                .build());

        String ownerId = sigChecker.getOwnerId();
        Context.require(labelInfo.checkOwner(ownerId), "You do not have permission.");

        var attrs = new LabelInfo.Builder();
        if (name != null) {
            attrs.name(name);
        }
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        var labelExpireAt = labelInfo.getExpire_at();
        if (expire_at.signum() > 0) {
            Context.require(expire_at.compareTo(blockTimestamp) > 0, "expire_at must be greater than blockTimestamp");
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
            Context.require(producer_expire_at.compareTo(blockTimestamp) > 0, "producer_expire_at must be greater than blockTimestamp");
            Context.require(producer_expire_at.compareTo(labelExpireAt) <= 0, "producer_expire_at must be less than equal to expire_at");
            attrs.producerExpireAt(producer_expire_at);
        }
        attrs.lastUpdated(Context.getBlockHeight());

        labelInfo.update(attrs);
        this.labelInfos.set(label_id, labelInfo);
        LabelUpdated(label_id);
    }

    @External
    public void add_data(String label_id,
                         String data,
                         String name,
                         BigInteger size,
                         String producer_sign) {
        var labelInfo = checkLabelId(label_id);

        var sigChecker = new SignatureChecker();
        verifySignature(sigChecker, producer_sign);
        validatePayload(sigChecker, new Payload.Builder("add_data")
                .labelId(label_id)
                .dataId(data)
                .build());

        String producer = sigChecker.getOwnerId();
        Context.require(labelInfo.getProducer().equals(producer), "unauthorized producer");

        // check producer_expire_at
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger producerExpireAt = labelInfo.getProducer_expire_at();
        Context.require(producerExpireAt.compareTo(blockTimestamp) > 0, "producer_expire_at has expired");

        addData(data, name, size, labelInfo);
    }

    private void addData(String data, String name, BigInteger size, LabelInfo labelInfo) {
        var dataInfo = new DataInfo(data, name, size);
        Context.require(labelInfo.addData(dataInfo), "data already exists");
        LabelData(labelInfo.getLabel_id(), data);
    }

    @External(readonly=true)
    public PageOfData get_data(String label_id,
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

        var sigChecker = new SignatureChecker();
        verifySignature(sigChecker, owner_sign);
        validatePayload(sigChecker, new Payload.Builder("add_policy")
                .labelId(label_id)
                .policyId(policy_id)
                .build());

        String ownerId = sigChecker.getOwnerId();
        Context.require(labelInfo.checkOwner(ownerId), "You do not have permission.");

        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        BigInteger expireAt = (expire_at.signum() == 0) ? labelInfo.getExpire_at() : expire_at;
        Context.require(expireAt.compareTo(blockTimestamp) > 0, "expire_at must be greater than blockTimestamp");
        Context.require(expireAt.compareTo(labelInfo.getExpire_at()) <= 0, "expire_at must be less than equal to the label's expire_at");

        var policyInfo = new PolicyInfo.Builder()
                .policyId(policy_id)
                .labelId(label_id)
                .name(name)
                .consumer(validateDid(consumer))
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

        var sigChecker = new SignatureChecker();
        verifySignature(sigChecker, owner_sign);
        validatePayload(sigChecker, new Payload.Builder("update_policy")
                .policyId(policy_id)
                .baseHeight(policyInfo.getLast_updated())
                .build());

        String ownerId = sigChecker.getOwnerId();
        Context.require(labelInfo.checkOwner(ownerId), "You do not have permission.");

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
        if (current.compareTo(policyExpireAt) <= 0) {
            // not expired: valid policy
            checked = true;
        }

        return Map.ofEntries(
                Map.entry("policy_id", policy_id),
                Map.entry("label_id", labelInfo.getLabel_id()),
                Map.entry("checked", checked),
                Map.entry("expire_at", policyExpireAt),
                Map.entry("label_expire_at", labelExpireAt)
        );
    }

    @External(readonly=true)
    public PageOfPolicy get_policies(String label_id,
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
    public void LabelData(String label_id, String data) {}

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
