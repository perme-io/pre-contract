package com.iconloop.score.pds;

import score.*;
import score.annotation.EventLog;
import score.annotation.External;
import score.annotation.Optional;
import score.annotation.Payable;

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

public class PdsPolicy {
    private static final BigInteger ONE_YEAR = new BigInteger("31536000000000");
    private static final BigInteger ONE_ICX = new BigInteger("1000000000000000000");

    private final ArrayDB<String> peers = Context.newArrayDB("peers", String.class);
    private final DictDB<String, LabelInfo> labelInfos;
    private final DictDB<String, NodeInfo> nodeInfos;
    private final DictDB<String, PolicyInfo> policyInfos;
    private final VarDB<BigInteger> labelCount = Context.newVarDB("labelCount", BigInteger.class);
    private final VarDB<BigInteger> policyCount = Context.newVarDB("policyCount", BigInteger.class);
    private final VarDB<BigInteger> minStakeForServe = Context.newVarDB("minStakeForServe", BigInteger.class);
    private final VarDB<Address> didSummaryScore = Context.newVarDB("didSummaryScore", Address.class);

    public PdsPolicy() {
        this.labelInfos = Context.newDictDB("labelInfos", LabelInfo.class);
        this.nodeInfos = Context.newDictDB("nodeInfos", NodeInfo.class);
        this.policyInfos = Context.newDictDB("policyInfos", PolicyInfo.class);
    }

    @External()
    public void set_did_summary_score(Address did_summary_score) {
        Context.require(Context.getCaller().equals(Context.getOwner()), "Only owner can call this method.");
        this.didSummaryScore.set(did_summary_score);
    }

    @External(readonly=true)
    public Address get_did_summary_score() {
        return this.didSummaryScore.getOrDefault(null);
    }

    @External()
    public void set_min_stake_value(BigInteger min_stake_for_serve) {
        Context.require(Context.getCaller().equals(Context.getOwner()), "Only owner can call this method.");
        this.minStakeForServe.set(min_stake_for_serve);
    }

    @External(readonly=true)
    public Map<String, Object> get_min_stake_value() {
        return Map.ofEntries(
                Map.entry("min_stake_for_serve", this.minStakeForServe.getOrDefault(BigInteger.valueOf(0)))
        );
    }

    @External(readonly=true)
    public Map<String, Object> get_label(String label_id) {
        LabelInfo labelInfo = this.labelInfos.get(label_id);
        return labelInfo.toMap();
    }

    @External()
    public void add_label(String label_id,
                          String name,
                          @Optional String owner_did,
                          @Optional byte[] owner_sign,
                          @Optional String producer,
                          @Optional String producer_expire_at,
                          @Optional String expire_at) {
        Context.require(!label_id.isEmpty(), "Blank key is not allowed.");
        Context.require(this.labelInfos.get(label_id) == null, "It has already been added.");

        BigInteger blockTimeStamp = new BigInteger(String.valueOf(Context.getBlockTimestamp()));
        String owner = Context.getCaller().toString();
        BigInteger contentNonce = BigInteger.ZERO;
        if (owner_did != null) {
            DidMessage didMessage = getDidMessage(owner_did, owner_sign);
            owner = didMessage.did;
            contentNonce = didMessage.nonce;
            Context.require(label_id.equals(didMessage.target), "Invalid Content(LabelInfo) target.");
        }

        String producerID = (producer == null) ? owner : producer;
        String producerExpireAt =  (producer_expire_at == null) ? String.valueOf(blockTimeStamp.add(ONE_YEAR)) : producer_expire_at;
        String expireAt =  (expire_at == null) ? String.valueOf(blockTimeStamp.add(ONE_YEAR)) : expire_at;

        LabelInfo labelInfo = new LabelInfo(label_id, name, owner, producerID, producerExpireAt, "", "", "", null, String.valueOf(Context.getBlockTimestamp()), expireAt, contentNonce);
        this.labelInfos.set(label_id, labelInfo);
        PDSEvent(EventType.AddLabel.name(), label_id, producerID, labelInfo.getNonce());

        BigInteger total = this.labelCount.getOrDefault(BigInteger.ZERO);
        this.labelCount.set(total.add(BigInteger.ONE));
    }

    // TODO Add @Optional owner_did, If fail in did auth, message has did auth fail.
    @External()
    public void remove_label(String label_id,
                             @Optional String owner_did,
                             @Optional byte[] owner_sign) {
        LabelInfo labelInfo = this.labelInfos.get(label_id);
        Context.require(labelInfo != null, "Invalid request target(label).");

        // Verify owner
        String owner = Context.getCaller().toString();
        if (owner_did != null) {
            DidMessage didMessage = getDidMessage(owner_did, owner_sign);
            owner = didMessage.did;
            Context.require(labelInfo.checkNonce(didMessage.nonce), "Invalid Content(LabelInfo) nonce.");
            Context.require(label_id.equals(didMessage.target), "Invalid Content(LabelInfo) target.");
        }
        if (!labelInfo.checkOwner(owner)) {
            Context.revert(101, "You do not have permission.");
        }

        String[] policyList = labelInfo.getPolicies();

        for (String policyId : policyList) {
            PolicyInfo policyInfo = this.policyInfos.get(policyId);
            if (policyInfo != null) {
                PDSEvent(EventType.RemovePolicy.name(), policyId, policyInfo.getConsumer(), policyInfo.getNonce());
                this.policyInfos.set(policyId, null);
            }
        }

        this.labelInfos.set(label_id, null);
        PDSEvent(EventType.RemoveLabel.name(), label_id, labelInfo.getProducer(), labelInfo.getNonce());

        BigInteger total = this.labelCount.getOrDefault(BigInteger.ZERO);
        this.labelCount.set(total.subtract(BigInteger.ONE));
    }

    // TODO Add @Optional owner_did
    @External()
    public void update_label(String label_id,
                             @Optional String owner_did,
                             @Optional byte[] owner_sign,
                             @Optional String name,
                             @Optional String producer,
                             @Optional String producer_expire_at,
                             @Optional String expire_at) {
        LabelInfo labelInfo = this.labelInfos.get(label_id);
        Context.require(labelInfo != null, "Invalid request target(label).");

        // Verify owner
        String owner = Context.getCaller().toString();
        if (owner_did != null) {
            DidMessage didMessage = getDidMessage(owner_did, owner_sign);
            owner = didMessage.did;
            Context.require(labelInfo.checkNonce(didMessage.nonce), "Invalid Content(LabelInfo) nonce.");
            Context.require(label_id.equals(didMessage.target), "Invalid Content(LabelInfo) target.");
        }

        if (!labelInfo.checkOwner(owner)) {
            Context.revert(101, "You do not have permission.");
        }

        BigInteger blockTimeStamp = new BigInteger(String.valueOf(Context.getBlockTimestamp()));
        String producerExpireAt = null;
        String producerAddress = labelInfo.getProducer();
        if (producer != null) {
            producerExpireAt = (producer_expire_at == null) ? String.valueOf(blockTimeStamp.add(ONE_YEAR)) : producer_expire_at;
            producerAddress = producer;
        }

        labelInfo.update(name, null, producerAddress, producerExpireAt, null, null, null, null, "", expire_at);
        this.labelInfos.set(label_id, labelInfo);
        PDSEvent(EventType.UpdateLabel.name(), label_id, labelInfo.getProducer(), labelInfo.getNonce());
    }

    @External()
    public void update_data(String label_id,
                            String data,
                            @Optional String producer_did,
                            @Optional byte[] producer_sign,
                            @Optional String capsule) {
        LabelInfo labelInfo = this.labelInfos.get(label_id);
        Context.require(labelInfo != null, "Invalid request target(label).");

        // Verify producer
        String producer = Context.getCaller().toString();
        if (producer_did != null) {
            DidMessage didMessage = getDidMessage(producer_did, producer_sign);
            producer = didMessage.did;
            Context.require(labelInfo.checkNonce(didMessage.nonce), "Invalid Content(LabelInfo) nonce.");
            Context.require(label_id.equals(didMessage.target), "Invalid Content(LabelInfo) target.");
        }
        if (!labelInfo.getProducer().equals(producer)) {
            Context.revert(101, "You do not have permission.");
        }

        labelInfo.update(null, null, null, null, capsule, data, String.valueOf(Context.getBlockTimestamp()), null, "", null);
        this.labelInfos.set(label_id, labelInfo);
        PDSEvent(EventType.UpdateData.name(), label_id, labelInfo.getProducer(), labelInfo.getNonce());
    }

    @External(readonly=true)
    public Map<String, Object> get_policy(String policy_id) {
        PolicyInfo policyInfo = this.policyInfos.get(policy_id);
        return policyInfo.toMap();
    }

    @External()
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
        BigInteger contentNonce = BigInteger.ZERO;
        if (owner_did != null) {
            DidMessage didMessage = getDidMessage(owner_did, owner_sign);
            owner = didMessage.did;
            contentNonce = didMessage.nonce;
            Context.require(policy_id.equals(didMessage.target), "Invalid Content(PolicyInfo) target.");
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
                policy_id, label_id, name, owner, consumer, threshold, proxy_number, proxies, String.valueOf(Context.getBlockTimestamp()), expireAt, contentNonce
        );
        this.policyInfos.set(policy_id, policyInfo);
        this.labelInfos.set(label_id, labelInfo);
        PDSEvent(EventType.AddPolicy.name(), policy_id, consumer, policyInfo.getNonce());

        BigInteger total = this.policyCount.getOrDefault(BigInteger.ZERO);
        this.policyCount.set(total.add(BigInteger.ONE));
    }

    @External()
    public void remove_policy(String policy_id, @Optional String owner_did, @Optional byte[] owner_sign) {
        PolicyInfo policyInfo = this.policyInfos.get(policy_id);
        Context.require(policyInfo != null, "Invalid request target(policy).");

        // Verify policy owner
        String owner = Context.getCaller().toString();
        if (owner_did != null) {
            DidMessage didMessage = getDidMessage(owner_did, owner_sign);
            owner = didMessage.did;
            Context.require(policyInfo.checkNonce(didMessage.nonce), "Invalid Content(PolicyInfo) nonce.");
            Context.require(policy_id.equals(didMessage.target), "Invalid Content(PolicyInfo) target.");
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
            PDSEvent(EventType.RemovePolicy.name(), policy_id, policyInfo.getConsumer(), policyInfo.getNonce());
        }

        this.policyInfos.set(policy_id, null);

        BigInteger total = this.policyCount.getOrDefault(BigInteger.ZERO);
        this.policyCount.set(total.subtract(BigInteger.ONE));
    }

    @External(readonly=true)
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

        BigInteger labelExpireAt = labelInfo.getExpireAt();
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
    public Map<String, Object> get_node(String peer_id) {
        NodeInfo nodeInfo = this.nodeInfos.get(peer_id);
        return nodeInfo.toMap();
    }

    @External()
    @Payable
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
        PDSEvent(EventType.AddNode.name(), peer_id, nodeInfo.getEndpoint(), 0);
    }

    @External()
    public void remove_node(String peer_id) {
        NodeInfo nodeInfo = this.nodeInfos.get(peer_id);
        Context.require(nodeInfo != null, "Invalid request target(node).");

        if (!nodeInfo.checkOwner(Context.getCaller())) {
            Context.revert(101, "You do not have permission.");
        }

        this.nodeInfos.set(peer_id, null);
        removeNode(peer_id);
        PDSEvent(EventType.RemoveNode.name(), peer_id, nodeInfo.getEndpoint(), 0);
    }

    @External()
    @Payable
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
        PDSEvent(EventType.UpdateNode.name(), peer_id, nodeInfo.getEndpoint(), 0);
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

    @External()
    public void reset__() {
        // TODO 개발 과정에서 컨트랙트 리셋 용도로 사용하는 임시 함수, 운영을 위한 배포시에는 이 메소드는 전체 제거되어야 함.
        // Check permission
        Context.require(Context.getOwner().equals(Context.getCaller()), "You do not have permission.");

        String peer_id;
        int peer_count = this.peers.size();
        for (int i = 0; i < peer_count; i++) {
            peer_id = this.peers.pop();
            this.nodeInfos.set(peer_id, null);
        }
    }

    @External(readonly = true)
    public List<Object> all_node() {
        Object[] allNode = new Object[this.peers.size()];

        for (int i=0; i < this.peers.size(); i++) {
            NodeInfo nodeInfo = this.nodeInfos.get(this.peers.get(i));
            allNode[i] = nodeInfo.toMap();
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
    private boolean verifySign(String msg, byte[] sign) {
        if (this.didSummaryScore.getOrDefault(null) == null) {
            Context.revert(102, "No External SCORE to verify DID.");
        }

        DidMessage didMessage = Helper.DidMessageParser(msg);
        String publicKey = (String) Context.call(this.didSummaryScore.get(), "getPublicKey", didMessage.did, didMessage.kid);

        byte[] msgHash = Context.hash("keccak-256", msg.getBytes());
        byte[] recoveredKeyBytes = Context.recoverKey("ecdsa-secp256k1", msgHash, sign, false);
        String recoveredKey = new BigInteger(recoveredKeyBytes).toString(16);

//        System.out.println("publicKey(verifySign): " + publicKey);
//        System.out.println("recoveredKey(verifySign): " + recoveredKey);

        return publicKey.equals(recoveredKey);
    }

    private DidMessage getDidMessage(String msg, byte[] sign) {
        Context.require(verifySign(msg, sign), "Invalid did signature.");
        return Helper.DidMessageParser(msg);
    }

    @Payable
    public void fallback() {
        // just receive incoming funds
    }

    /*
     * Events
     */
    @EventLog
    protected void PDSEvent(String event, String value1, String value2, int nonce) {}
}
