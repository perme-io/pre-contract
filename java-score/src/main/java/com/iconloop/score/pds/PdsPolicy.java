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
    private final DictDB<String, String> labelInfos;
    private final DictDB<String, String> policyInfos;
    private final DictDB<String, String> nodeInfos;
    private final VarDB<BigInteger> labelCount = Context.newVarDB("labelCount", BigInteger.class);
    private final VarDB<BigInteger> policyCount = Context.newVarDB("policyCount", BigInteger.class);
    private final VarDB<BigInteger> minStakeForServe = Context.newVarDB("minStakeForServe", BigInteger.class);

    public PdsPolicy() {
        this.labelInfos = Context.newDictDB("labelInfos", String.class);
        this.policyInfos = Context.newDictDB("policyInfos", String.class);
        this.nodeInfos = Context.newDictDB("nodeInfos", String.class);
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
        LabelInfo labelInfo = LabelInfo.fromString(this.labelInfos.get(label_id));
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
        LabelInfo labelInfo = new LabelInfo(label_id);
        Context.require(!label_id.isEmpty(), "Blank key is not allowed.");
        Context.require(this.labelInfos.getOrDefault(label_id, "").isEmpty(), "It has already been added.");

        BigInteger blockTimeStamp = new BigInteger(String.valueOf(Context.getBlockTimestamp()));
        String owner = Context.getCaller().toString();
        if (owner_did != null) {
            Context.require(verifySign(owner_did, owner_sign), "Invalid did signature.");
            owner = owner_did;
        }

        String producerID = (producer == null) ? owner : producer;
        String producerExpireAt =  (producer_expire_at == null) ? String.valueOf(blockTimeStamp.add(ONE_YEAR)) : producer_expire_at;
        String expireAt =  (expire_at == null) ? String.valueOf(blockTimeStamp.add(ONE_YEAR)) : expire_at;

        labelInfo.fromParams(name, owner, producerID, producerExpireAt, null, null, null, null, String.valueOf(Context.getBlockTimestamp()), expireAt);
        this.labelInfos.set(label_id, labelInfo.toString());
        PDSEvent(EventType.AddLabel.name(), label_id, producerID);

        BigInteger total = this.labelCount.getOrDefault(BigInteger.ZERO);
        this.labelCount.set(total.add(BigInteger.ONE));
    }

    // TODO Add @Optional owner_did, If fail in did auth, message has did auth fail.
    @External()
    public void remove_label(String label_id,
                             @Optional String owner_did,
                             @Optional byte[] owner_sign) {
        Context.require(!this.labelInfos.getOrDefault(label_id, "").isEmpty(), "Invalid request target.");

        LabelInfo labelInfo = LabelInfo.fromString(this.labelInfos.get(label_id));
        // Verify owner
        String owner = Context.getCaller().toString();
        if (owner_did != null) {
            Context.require(verifySign(owner_did, owner_sign), "Invalid did signature.");
            owner = owner_did;
        }
        if (!labelInfo.getString("owner").equals(owner)) {
            Context.revert(101, "You do not have permission.");
        }

        String[] policyList = labelInfo.policyList();

        for (String policyId : policyList) {
            if (!this.policyInfos.getOrDefault(policyId, "").isEmpty()) {
                PolicyInfo policyInfo = PolicyInfo.fromString(this.policyInfos.get(policyId));
                PDSEvent(EventType.RemovePolicy.name(), policyId, policyInfo.getString("consumer"));
                this.policyInfos.set(policyId, "");
            }
        }

        this.labelInfos.set(label_id, "");
        PDSEvent(EventType.RemoveLabel.name(), label_id, labelInfo.getString("producer"));

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
        Context.require(!this.labelInfos.getOrDefault(label_id, "").isEmpty(), "Invalid request target.");

        LabelInfo labelInfo = LabelInfo.fromString(this.labelInfos.get(label_id));

        // Verify owner
        String owner = Context.getCaller().toString();
        if (owner_did != null) {
            Context.require(verifySign(owner_did, owner_sign), "Invalid did signature.");
            owner = owner_did;
        }
        if (!labelInfo.getString("owner").equals(owner)) {
            Context.revert(101, "You do not have permission.");
        }

        BigInteger blockTimeStamp = new BigInteger(String.valueOf(Context.getBlockTimestamp()));
        String producerExpireAt = null;
        String producerAddress = labelInfo.getString("producer");
        if (producer != null) {
            producerExpireAt = (producer_expire_at == null) ? String.valueOf(blockTimeStamp.add(ONE_YEAR)) : producer_expire_at;
            producerAddress = producer;
        }

        labelInfo.fromParams(name, null, producerAddress, producerExpireAt, null, null, null, null, "", expire_at);
        this.labelInfos.set(label_id, labelInfo.toString());
        PDSEvent(EventType.UpdateLabel.name(), label_id, labelInfo.getString("producer"));
    }

    @External()
    public void update_data(String label_id,
                            String data,
                            @Optional String producer_did,
                            @Optional byte[] producer_sign,
                            @Optional String capsule) {
        Context.require(!this.labelInfos.getOrDefault(label_id, "").isEmpty(), "Invalid request target.");

        LabelInfo labelInfo = LabelInfo.fromString(this.labelInfos.get(label_id));

        // Verify producer
        String producer = Context.getCaller().toString();
        if (producer_did != null) {
            Context.require(verifySign(producer_did, producer_sign), "Invalid did signature.");
            producer = producer_did;
        }
        if (!labelInfo.getString("producer").equals(producer)) {
            Context.revert(101, "You do not have permission.");
        }

        labelInfo.fromParams(null, null, null, null, capsule, data, String.valueOf(Context.getBlockTimestamp()), null, "", null);
        this.labelInfos.set(label_id, labelInfo.toString());
        PDSEvent(EventType.UpdateData.name(), label_id, labelInfo.getString("producer"));
    }

    @External(readonly=true)
    public Map<String, Object> get_policy(String policy_id) {
        PolicyInfo policyInfo = PolicyInfo.fromString(this.policyInfos.get(policy_id));
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
        PolicyInfo policyInfo = new PolicyInfo(policy_id);
        Context.require(!policy_id.isEmpty(), "Blank key is not allowed.");
        Context.require(this.policyInfos.getOrDefault(policy_id, "").isEmpty(), "It has already been added.");

        LabelInfo labelInfo = LabelInfo.fromString(this.labelInfos.get(label_id));
        String owner = Context.getCaller().toString();
        if (owner_did != null) {
            Context.require(verifySign(owner_did, owner_sign), "Invalid did signature.");
            owner = owner_did;
        }
        if (!labelInfo.getString("owner").equals(owner)) {
            Context.revert(101, "You do not have permission.");
        }

        String[] prePolicyList = labelInfo.policyList();
        String[] newPolicyList = new String[prePolicyList.length + 1];
        System.arraycopy(prePolicyList, 0, newPolicyList, 0, prePolicyList.length);
        newPolicyList[prePolicyList.length] = policy_id;
        labelInfo.getJsonObject().set("policies", Helper.StringListToJsonString(newPolicyList));

        BigInteger blockTimeStamp = new BigInteger(String.valueOf(Context.getBlockTimestamp()));
        String expireAt =  (expire_at == null) ? String.valueOf(blockTimeStamp.add(ONE_YEAR)) : expire_at;

        policyInfo.fromParams(
                label_id, name, owner, consumer, threshold, proxy_number, proxies, String.valueOf(Context.getBlockTimestamp()), expireAt
        );
        this.policyInfos.set(policy_id, policyInfo.toString());
        this.labelInfos.set(label_id, labelInfo.toString());
        PDSEvent(EventType.AddPolicy.name(), policy_id, consumer);

        BigInteger total = this.policyCount.getOrDefault(BigInteger.ZERO);
        this.policyCount.set(total.add(BigInteger.ONE));
    }

    @External()
    public void remove_policy(String policy_id, @Optional String owner_did, @Optional byte[] owner_sign) {
        Context.require(!this.policyInfos.getOrDefault(policy_id, "").isEmpty(), "Invalid request target.");

        PolicyInfo policyInfo = PolicyInfo.fromString(this.policyInfos.get(policy_id));
        // Verify policy owner
        String owner = Context.getCaller().toString();
        if (owner_did != null) {
            Context.require(verifySign(owner_did, owner_sign), "Invalid did signature.");
            owner = owner_did;
        }
        if (!policyInfo.getString("owner").equals(owner)) {
            Context.revert(101, "You do not have permission.");
        }

        String labelId = policyInfo.getString("label_id");
        LabelInfo labelInfo = LabelInfo.fromString(this.labelInfos.get(labelId));
        // Verify label owner
        if (!labelInfo.getString("owner").equals(owner)) {
            Context.revert(101, "You do not have permission.");
        }

        String[] prePolicyList = labelInfo.policyList();
        if (prePolicyList.length > 0) {
            String[] newPolicyList = new String[prePolicyList.length - 1];

            int newIndex = 0;
            for (String s : prePolicyList) {
                if (!s.equals(policy_id)) {
                    newPolicyList[newIndex] = s;
                    newIndex++;
                }
            }
            labelInfo.getJsonObject().set("policies", Helper.StringListToJsonString(newPolicyList));

            this.labelInfos.set(labelId, labelInfo.toString());
            PDSEvent(EventType.RemovePolicy.name(), policy_id, policyInfo.getString("consumer"));
        }

        this.policyInfos.set(policy_id, "");

        BigInteger total = this.policyCount.getOrDefault(BigInteger.ZERO);
        this.policyCount.set(total.subtract(BigInteger.ONE));
    }

    @External(readonly=true)
    public Map<String, Object> check_policy(String policy_id,
                                            @Optional String owner,
                                            @Optional String consumer) {
        Context.require(!this.policyInfos.getOrDefault(policy_id, "").isEmpty(), "Invalid request target.");
        boolean checked = owner != null || consumer != null;

        PolicyInfo policyInfo = PolicyInfo.fromString(this.policyInfos.get(policy_id));
        String labelId = policyInfo.getString("label_id");

        Context.require(!this.labelInfos.getOrDefault(labelId, "").isEmpty(), "Invalid request target.");
        LabelInfo labelInfo = LabelInfo.fromString(this.labelInfos.get(labelId));

        if (owner != null) {
            if (!policyInfo.getString("owner").equals(owner)) {
                checked = false;
            }

            if (!labelInfo.getString("owner").equals(owner)) {
                checked = false;
            }
        }

        if (consumer != null) {
            if (!policyInfo.getString("consumer").equals(consumer)) {
                checked = false;
            }
        }

        BigInteger labelExpireAt = BigInteger.ZERO;
        if (!labelInfo.getString("expire_at").isEmpty()) {
            labelExpireAt = new BigInteger(labelInfo.getString("expire_at"));
        }

        BigInteger policyExpireAt = BigInteger.ZERO;
        if (!policyInfo.getString("expire_at").isEmpty()) {
            policyExpireAt = new BigInteger(policyInfo.getString("expire_at"));
        }

        String expireAt = "";
        if (labelExpireAt.compareTo(policyExpireAt) > 0) {
            expireAt = policyExpireAt.toString();
        } else {
            expireAt = labelExpireAt.toString();
        }

        return Map.ofEntries(
                Map.entry("policy_id", policy_id),
                Map.entry("label_id", labelId),
                Map.entry("name", policyInfo.getString("name")),
                Map.entry("checked", checked),
                Map.entry("expire_at", expireAt)
        );
    }

    @External(readonly=true)
    public Map<String, Object> get_node(String peer_id) {
        NodeInfo nodeInfo = NodeInfo.fromString(this.nodeInfos.get(peer_id));
        return nodeInfo.toMap(this.peers.size());
    }

    @External()
    @Payable
    public void add_node(String peer_id,
                         @Optional String endpoint,
                         @Optional String name,
                         @Optional String comment,
                         @Optional Address owner) {
        NodeInfo nodeInfo = new NodeInfo(peer_id);
        Context.require(!peer_id.isEmpty(), "Blank key is not allowed.");
        Context.require(this.nodeInfos.getOrDefault(peer_id, "").isEmpty(), "It has already been added.");

        Address ownerAddress = (owner == null) ? Context.getCaller() : owner;
        BigInteger stake = this.minStakeForServe.getOrDefault(BigInteger.ZERO);

        if (stake != BigInteger.ZERO) {
            // You need at least this.minStakeForServe(icx) to add a node.
            Context.require(Context.getValue().compareTo(ONE_ICX.multiply(stake)) >= 0);
            stake = Context.getValue();
        }

        nodeInfo.fromParams(endpoint, name, comment, String.valueOf(Context.getBlockTimestamp()), ownerAddress, stake, BigInteger.valueOf(0));
        this.nodeInfos.set(peer_id, nodeInfo.toString());

        removeNode(peer_id);
        this.peers.add(peer_id);
        PDSEvent(EventType.AddNode.name(), peer_id, nodeInfo.getString("endpoint"));
    }

    @External()
    public void remove_node(String peer_id) {
        Context.require(!this.nodeInfos.getOrDefault(peer_id, "").isEmpty(), "Invalid request target.");

        NodeInfo nodeInfo = NodeInfo.fromString(this.nodeInfos.get(peer_id));
        if (!nodeInfo.getString("owner").equals(Context.getCaller().toString())) {
            Context.revert(101, "You do not have permission.");
        }

        this.nodeInfos.set(peer_id, "");
        removeNode(peer_id);
        PDSEvent(EventType.RemoveNode.name(), peer_id, nodeInfo.getString("endpoint"));
    }

    @External()
    @Payable
    public void update_node(String peer_id,
                            @Optional String endpoint,
                            @Optional String name,
                            @Optional String comment,
                            @Optional Address owner) {
        Context.require(!this.nodeInfos.getOrDefault(peer_id, "").isEmpty(), "Invalid request target.");

        NodeInfo nodeInfo = NodeInfo.fromString(this.nodeInfos.get(peer_id));
        if (!nodeInfo.getString("owner").equals(Context.getCaller().toString())) {
            Context.revert(101, "You do not have permission.");
        }

        Address ownerAddress = (owner == null) ? Context.getCaller() : owner;
        BigInteger stake = this.minStakeForServe.getOrDefault(BigInteger.ZERO);

        if (stake != BigInteger.ZERO) {
            BigInteger prevStake = new BigInteger(nodeInfo.getString("stake"), 16);
            BigInteger newStake = prevStake.add(Context.getValue());
            Context.require(newStake.compareTo(ONE_ICX.multiply(stake)) >= 0);
            stake = newStake;
        }

        nodeInfo.fromParams(endpoint, name, comment, "", ownerAddress, stake, BigInteger.valueOf(0));
        this.nodeInfos.set(peer_id, nodeInfo.toString());

        removeNode(peer_id);
        this.peers.add(peer_id);
        PDSEvent(EventType.UpdateNode.name(), peer_id, nodeInfo.getString("endpoint"));
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
            this.nodeInfos.set(peer_id, "");
        }
    }

    @External(readonly = true)
    public List<Object> all_node() {
        Object[] allNode = new Object[this.peers.size()];

        for (int i=0; i < this.peers.size(); i++) {
            NodeInfo nodeInfo = NodeInfo.fromString(this.nodeInfos.get(this.peers.get(i)));
            allNode[i] = nodeInfo.toMap(this.peers.size());
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
        byte[] msgHash = Context.hash("sha3-256", msg.getBytes());
        byte[] publicKey = Context.recoverKey("ecdsa-secp256k1", msgHash, sign, false);
        return Context.verifySignature("ecdsa-secp256k1", msgHash, sign, publicKey);
    }

    @Payable
    public void fallback() {
        // just receive incoming funds
    }

    /*
     * Events
     */
    @EventLog
    protected void PDSEvent(String event, String value1, String value2) {}
}
