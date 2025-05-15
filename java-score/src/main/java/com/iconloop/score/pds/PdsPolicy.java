package com.iconloop.score.pds;

import com.parametacorp.jwt.Payload;
import com.parametacorp.util.Converter;
import score.Address;
import score.ArrayDB;
import score.Context;
import score.DictDB;
import score.VarDB;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;
import scorex.util.StringTokenizer;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

enum EventType {
    AddLabel,
    RemoveLabel,
    UpdateLabel,
    UpdateData,
    AddPolicy,
    RemovePolicy,
    AddNode,
    RemoveNode,
    UpdateNode
}

public class PdsPolicy implements Label, Policy, Node {
    private static final BigInteger ONE_YEAR = new BigInteger("31536000000000");
    private static final BigInteger ONE_ICX = new BigInteger("1000000000000000000");

    private final ArrayDB<String> peers = Context.newArrayDB("peers", String.class);
    private final DictDB<String, LabelInfo> labelInfos = Context.newDictDB("labelInfos", LabelInfo.class);
    private final DictDB<String, NodeInfo> nodeInfos = Context.newDictDB("nodeInfos", NodeInfo.class);
    private final DictDB<String, PolicyInfo> policyInfos = Context.newDictDB("policyInfos", PolicyInfo.class);
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
        Context.require(sigChecker.verifySig(get_did_score(), owner_sign), "failed to verify owner_sign");
        var expected = new Payload.Builder("add_label")
                .labelId(label_id)
                .build();
        Context.require(sigChecker.validatePayload(expected), "failed to validate payload");

        String ownerId = sigChecker.getOwnerId();
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        Context.require(expire_at.compareTo(blockTimestamp) > 0, "expire_at must be greater than blockTimestamp");

        String producerId = (producer == null) ? ownerId : validateDid(producer);
        BigInteger producerExpireAt = (producer_expire_at.signum() == 0) ? expire_at : producer_expire_at;
        Context.require(producerExpireAt.compareTo(blockTimestamp) > 0, "producer_expire_at must be greater than blockTimestamp");
        Context.require(producerExpireAt.compareTo(expire_at) <= 0, "producer_expire_at must be less than equal to expire_at");

        var labelBuilder = new LabelInfo.Builder()
                .labelId(label_id)
                .owner(ownerId)
                .name(name)
                .publicKey(public_key)
                .expireAt(expire_at)
                .category(category)
                .producer(producerId)
                .producerExpireAt(producerExpireAt)
                .created(Context.getBlockHeight());

        // TODO: handle data & data_size

        var labelInfo = labelBuilder.build();
        this.labelInfos.set(label_id, labelInfo);
        LabelAdded(label_id, ownerId, producerId);

        BigInteger total = get_label_count();
        this.labelCount.set(total.add(BigInteger.ONE));
    }

    @External
    public void remove_label(String label_id,
                             String owner_sign) {
        var labelInfo = checkLabelId(label_id);

        var sigChecker = new SignatureChecker();
        Context.require(sigChecker.verifySig(get_did_score(), owner_sign), "failed to verify owner_sign");
        var expected = new Payload.Builder("remove_label")
                .labelId(label_id)
                .build();
        Context.require(sigChecker.validatePayload(expected), "failed to validate payload");

        String ownerId = sigChecker.getOwnerId();
        Context.require(labelInfo.checkOwner(ownerId), "You do not have permission.");

        // TODO: handle policies

        labelInfo.revoke(Context.getBlockHeight());
        this.labelInfos.set(label_id, labelInfo);
        LabelRemoved(label_id);

        BigInteger total = get_label_count();
        this.labelCount.set(total.subtract(BigInteger.ONE));
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
        Context.require(sigChecker.verifySig(get_did_score(), owner_sign), "failed to verify owner_sign");
        var expected = new Payload.Builder("update_label")
                .labelId(label_id)
                .baseHeight(labelInfo.getLast_updated())
                .build();
        Context.require(sigChecker.validatePayload(expected), "failed to validate payload");

        String ownerId = sigChecker.getOwnerId();
        Context.require(labelInfo.checkOwner(ownerId), "You do not have permission.");

        var attrs = new LabelInfo.Builder();
        if (name != null) {
            attrs.name(name);
        }
        BigInteger blockTimestamp = BigInteger.valueOf(Context.getBlockTimestamp());
        if (expire_at.signum() > 0) {
            Context.require(expire_at.compareTo(blockTimestamp) > 0, "expire_at must be greater than blockTimestamp");
            attrs.expireAt(expire_at);
        }
        if (category != null) {
            attrs.category(category);
        }
        if (producer != null) {
            attrs.producer(validateDid(producer));
        }
        if (producer_expire_at.signum() > 0) {
            Context.require(producer_expire_at.compareTo(blockTimestamp) > 0, "producer_expire_at must be greater than blockTimestamp");
            Context.require(producer_expire_at.compareTo(expire_at) <= 0, "producer_expire_at must be less than equal to expire_at");
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
        Context.require(sigChecker.verifySig(get_did_score(), producer_sign), "failed to verify producer_sign");
        var expected = new Payload.Builder("add_data")
                .labelId(label_id)
                .dataId(data)
                .build();
        Context.require(sigChecker.validatePayload(expected), "failed to validate payload");

        String producer = sigChecker.getOwnerId();
        Context.require(labelInfo.getProducer().equals(producer), "unauthorized producer");

        var dataInfo = new DataInfo(data, name, size);
        Context.require(labelInfo.addData(dataInfo), "data already exists");
        LabelData(label_id, data);
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

    @External
    public void add_policy(String policy_id,
                           String label_id,
                           String name,
                           String consumer,
                           BigInteger threshold,
                           String owner_sign,
                           @Optional BigInteger expire_at) {
    }

    public void add_policy(String policy_id,
                           String label_id,
                           String name,
                           String consumer,
                           BigInteger threshold,
                           BigInteger proxy_number,
                           @Optional String owner_did,
                           @Optional byte[] owner_sign,
                           @Optional String[] proxies,
                           @Optional String expire_at) {
        Context.require(!policy_id.isEmpty(), "Blank key is not allowed.");
        Context.require(this.policyInfos.get(policy_id) == null, "It has already been added.");

        LabelInfo labelInfo = this.labelInfos.get(label_id);
        Context.require(labelInfo != null, "Invalid request target(label).");

        String owner = Context.getCaller().toString();
        if (owner_did != null) {
            DidMessage didMessage = getDidMessage(owner_did, Context.getCaller(), policy_id, "add_policy", BigInteger.ZERO, owner_sign);
            owner = didMessage.did;
            Context.require(policy_id.equals(didMessage.getTarget()), "Invalid Content(PolicyInfo) target.");
        }
        if (!labelInfo.checkOwner(owner)) {
            Context.revert(101, "You do not have permission.");
        }

        String[] prePolicyList = labelInfo.getPolicies();
        String[] newPolicyList = new String[prePolicyList.length + 1];
        System.arraycopy(prePolicyList, 0, newPolicyList, 0, prePolicyList.length);
        newPolicyList[prePolicyList.length] = policy_id;
        labelInfo.setPolicies(newPolicyList);

        BigInteger blockTimeStamp = new BigInteger(String.valueOf(Context.getBlockTimestamp()));
        String expireAt =  (expire_at == null) ? String.valueOf(blockTimeStamp.add(ONE_YEAR)) : expire_at;

        PolicyInfo policyInfo = new PolicyInfo(
                policy_id, label_id, name, owner, consumer, threshold, proxy_number, proxies, String.valueOf(Context.getBlockTimestamp()), expireAt, null
        );
        this.policyInfos.set(policy_id, policyInfo);
        this.labelInfos.set(label_id, labelInfo);
        PDSEvent(EventType.AddPolicy.name(), policy_id, consumer, policyInfo.getLastUpdated());

        BigInteger total = this.policyCount.getOrDefault(BigInteger.ZERO);
        this.policyCount.set(total.add(BigInteger.ONE));
    }

    @External
    public void update_policy(String policy_id,
                              BigInteger expire_at,
                              String owner_sign) {

    }

    public void remove_policy(String policy_id, @Optional String owner_did, @Optional byte[] owner_sign) {
        PolicyInfo policyInfo = this.policyInfos.get(policy_id);
        Context.require(policyInfo != null, "Invalid request target(policy).");

        // Verify policy owner
        String owner = Context.getCaller().toString();
        if (owner_did != null) {
            DidMessage didMessage = getDidMessage(owner_did, Context.getCaller(), policy_id, "remove_policy", policyInfo.getLastUpdated(), owner_sign);
            owner = didMessage.did;
            Context.require(policyInfo.checkLastUpdated(didMessage.getLastUpdated()), "Invalid Content(PolicyInfo) lastUpdated.");
            Context.require(policy_id.equals(didMessage.getTarget()), "Invalid Content(PolicyInfo) target.");
        }
        if (!policyInfo.checkOwner(owner)) {
            Context.revert(101, "You do not have permission.");
        }

        LabelInfo labelInfo = this.labelInfos.get(policyInfo.getLabelId());
        // Verify label owner
        if (!labelInfo.checkOwner(owner)) {
            Context.revert(101, "You do not have permission.");
        }

        String[] prePolicyList = labelInfo.getPolicies();
        if (prePolicyList.length > 0) {
            String[] newPolicyList = new String[prePolicyList.length - 1];

            int newIndex = 0;
            for (String s : prePolicyList) {
                if (!s.equals(policy_id)) {
                    newPolicyList[newIndex] = s;
                    newIndex++;
                }
            }
            labelInfo.setPolicies(newPolicyList);

            this.labelInfos.set(policyInfo.getLabelId(), labelInfo);
            PDSEvent(EventType.RemovePolicy.name(), policy_id, policyInfo.getConsumer(), policyInfo.getLastUpdated());
        }

        this.policyInfos.set(policy_id, null);

        BigInteger total = this.policyCount.getOrDefault(BigInteger.ZERO);
        this.policyCount.set(total.subtract(BigInteger.ONE));
    }

    @External(readonly=true)
    public Map<String, Object> check_policy(String policy_id) {
        return Map.of();
    }

    public Map<String, Object> check_policy(String policy_id,
                                            @Optional String owner,
                                            @Optional String consumer) {
        PolicyInfo policyInfo = this.policyInfos.get(policy_id);
        Context.require(policyInfo != null, "Invalid request target(policy).");
        boolean checked = owner != null || consumer != null;

        LabelInfo labelInfo = this.labelInfos.get(policyInfo.getLabelId());
        Context.require(labelInfo != null, "Invalid request target(label).");

        if (owner != null) {
            if (!policyInfo.checkOwner(owner)) {
                checked = false;
            }

            if (!labelInfo.checkOwner(owner)) {
                checked = false;
            }
        }

        if (consumer != null) {
            if (!policyInfo.getConsumer().equals(consumer)) {
                checked = false;
            }
        }

        BigInteger labelExpireAt = labelInfo.getExpire_at();
        BigInteger policyExpireAt = BigInteger.ZERO;
        if (!policyInfo.getExpireAt().isEmpty()) {
            policyExpireAt = new BigInteger(policyInfo.getExpireAt());
        }

        String expireAt = "";
        if (labelExpireAt.compareTo(policyExpireAt) > 0) {
            expireAt = policyExpireAt.toString();
        } else {
            expireAt = labelExpireAt.toString();
        }

        return Map.ofEntries(
                Map.entry("policy_id", policy_id),
                Map.entry("label_id", policyInfo.getLabelId()),
                Map.entry("name", policyInfo.getName()),
                Map.entry("checked", checked),
                Map.entry("expire_at", expireAt)
        );
    }

    @External(readonly=true)
    public Page<PolicyInfo> get_policies(String label_id,
                                         BigInteger offset,
                                         @Optional BigInteger limit) {
        return null;
    }

    @External(readonly=true)
    public NodeInfo get_node(String peer_id) {
        return this.nodeInfos.get(peer_id);
    }

    @External
    @Payable
    public void add_node(String peer_id,
                         String name,
                         String endpoint,
                         @Optional Address owner) {

    }

    public void add_node(String peer_id,
                         @Optional String endpoint,
                         @Optional String name,
                         @Optional String comment,
                         @Optional Address owner) {
        Context.require(!peer_id.isEmpty(), "Blank key is not allowed.");
        Context.require(this.nodeInfos.get(peer_id) == null, "It has already been added.");

        Address ownerAddress = (owner == null) ? Context.getCaller() : owner;
        BigInteger stake = this.minStakeForServe.getOrDefault(BigInteger.ZERO);

        if (!stake.equals(BigInteger.ZERO)) {
            // You need at least this.minStakeForServe(icx) to add a node.
            Context.require(Context.getValue().compareTo(ONE_ICX.multiply(stake)) >= 0);
            stake = Context.getValue();
        }

        NodeInfo nodeInfo = new NodeInfo(peer_id, name, endpoint, comment, String.valueOf(Context.getBlockTimestamp()), ownerAddress, stake, BigInteger.valueOf(0));
        this.nodeInfos.set(peer_id, nodeInfo);

        removeNode(peer_id);
        this.peers.add(peer_id);
        PDSEvent(EventType.AddNode.name(), peer_id, nodeInfo.getEndpoint(), BigInteger.ZERO);
    }

    @External
    public void remove_node(String peer_id) {
        NodeInfo nodeInfo = this.nodeInfos.get(peer_id);
        Context.require(nodeInfo != null, "Invalid request target(node).");

        if (!nodeInfo.checkOwner(Context.getCaller())) {
            Context.revert(101, "You do not have permission.");
        }

        this.nodeInfos.set(peer_id, null);
        removeNode(peer_id);
        PDSEvent(EventType.RemoveNode.name(), peer_id, nodeInfo.getEndpoint(), BigInteger.ZERO);
    }

    @External
    @Payable
    public void update_node(String peer_id,
                            @Optional Address owner,
                            @Optional String name,
                            @Optional String endpoint) {

    }

    public void update_node(String peer_id,
                            @Optional String endpoint,
                            @Optional String name,
                            @Optional String comment,
                            @Optional Address owner) {
        NodeInfo nodeInfo = this.nodeInfos.get(peer_id);
        Context.require(nodeInfo != null, "Invalid request target(node).");

        if (!nodeInfo.checkOwner(Context.getCaller())) {
            Context.revert(101, "You do not have permission.");
        }

        Address ownerAddress = (owner == null) ? Context.getCaller() : owner;
        BigInteger stake = this.minStakeForServe.getOrDefault(BigInteger.ZERO);

        if (!stake.equals(BigInteger.ZERO)) {
            BigInteger prevStake =nodeInfo.getStake();
            BigInteger newStake = prevStake.add(Context.getValue());
            Context.require(newStake.compareTo(ONE_ICX.multiply(stake)) >= 0);
            stake = newStake;
        }

        nodeInfo.update(name, endpoint, comment, null, ownerAddress, stake, BigInteger.valueOf(0));
        this.nodeInfos.set(peer_id, nodeInfo);

        removeNode(peer_id);
        this.peers.add(peer_id);
        PDSEvent(EventType.UpdateNode.name(), peer_id, nodeInfo.getEndpoint(), BigInteger.ZERO);
    }

    private void removeNode(String peer_id) {
        if (!checkPeerExist(peer_id)) {
            return;
        }

        String top = this.peers.pop();
        if (!top.equals(peer_id)) {
            for (int i = 0; i < this.peers.size(); i++) {
                if (peer_id.equals(this.peers.get(i))) {
                    this.peers.set(i, top);
                    break;
                }
            }
        }
    }

    @External(readonly=true)
    public List<NodeInfo> all_nodes() {
        NodeInfo[] allNode = new NodeInfo[this.peers.size()];

        for (int i=0; i < this.peers.size(); i++) {
            NodeInfo nodeInfo = this.nodeInfos.get(this.peers.get(i));
            allNode[i] = nodeInfo;
        }

        return List.of(allNode);
    }

    @External(readonly=true)
    public BigInteger get_label_count() {
        return this.labelCount.getOrDefault(BigInteger.ZERO);
    }

    @External(readonly=true)
    public BigInteger get_policy_count() {
        return this.policyCount.getOrDefault(BigInteger.ZERO);
    }

    private boolean checkPeerExist(String peer_id) {
        //TODO: iteration is not efficient. Consider to use a Map.
        for (int i = 0; i < this.peers.size(); i++) {
            if (peer_id.equals(this.peers.get(i))) {
                return true;
            }
        }
        return false;
    }

    // Verify secp256k1 recoverable signature
    private boolean verifySign(DidMessage msg, byte[] sign) {
        if (this.didScore.getOrDefault(null) == null) {
            Context.revert(102, "No External SCORE to verify DID.");
        }

        String publicKey = (String) Context.call(this.didScore.get(), "getPublicKey", msg.did, msg.kid);
        byte[] recoveredKeyBytes = Context.recoverKey("ecdsa-secp256k1", msg.getHashedMessage(), sign, false);
        String recoveredKey = new BigInteger(recoveredKeyBytes).toString(16);

//        System.out.println("publicKey(verifySign): " + publicKey);
//        System.out.println("recoveredKey(verifySign): " + recoveredKey);

        return publicKey.equals(recoveredKey);
    }

    private DidMessage getDidMessage(String msg, Address from, String target, String method, BigInteger lastUpdated, byte[] sign) {
        DidMessage message = DidMessage.parse(msg);
        message.update(from, target, method, lastUpdated);
        byte[] hashedMessage = Context.hash("keccak-256", message.getMessageForHash());
        message.setHashedMessage(hashedMessage);

//        System.out.println("receivedMessage: " + msg);
//        System.out.println("generatedMessage: " + message.getMessage());
        Context.require(message.getMessage().equals(msg), "Invalid did message.");
        Context.require(verifySign(message, sign), "Invalid did signature.");
        return message;
    }

    /*
     * Events
     */
    @EventLog
    protected void PDSEvent(String event, String value1, String value2, BigInteger lastUpdated) {}

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

    @EventLog(indexed=2)
    public void NodeAdded(String peer_id, Address owner) {}

    @EventLog(indexed=2)
    public void NodeUpdated(String peer_id, Address owner) {}

    @EventLog(indexed=1)
    public void NodeRemoved(String peer_id) {}
}
