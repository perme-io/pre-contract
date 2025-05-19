package com.iconloop.score.pds;

import score.Address;
import score.ObjectReader;
import score.ObjectWriter;

import java.math.BigInteger;

public class NodeInfo {
    private final String peer_id;
    private String name;
    private String endpoint;
    private Address owner;
    private final long created;
    private BigInteger stake;
    private BigInteger reward;

    public NodeInfo(String peerId,
                    String name,
                    String endpoint,
                    Address owner,
                    long created,
                    BigInteger stake,
                    BigInteger reward) {
        this.peer_id = peerId;
        this.name = name;
        this.endpoint = endpoint;
        this.owner = owner;
        this.created = created;
        this.stake = stake;
        this.reward = reward;
    }

    public void update(String name,
                       String endpoint,
                       Address owner,
                       BigInteger stake,
                       BigInteger reward) {
        this.name = (name == null) ? this.name : name;
        this.endpoint = (endpoint == null) ? this.endpoint : endpoint;
        this.owner = (owner == null) ? this.owner : owner;
        this.stake = (stake == null) ? this.stake : stake;
        this.reward = (reward == null) ? this.reward : reward;
    }

    public String getPeer_id() {
        return peer_id;
    }

    public String getName() {
        return name;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public Address getOwner() {
        return owner;
    }

    public long getCreated() {
        return created;
    }

    public BigInteger getStake() {
        return stake;
    }

    public BigInteger getReward() {
        return reward;
    }

    public boolean checkOwner(Address owner) {
        return this.owner.equals(owner);
    }

    @Override
    public String toString() {
        return "NodeInfo{" +
                "peer_id='" + peer_id + '\'' +
                ", name='" + name + '\'' +
                ", endpoint='" + endpoint + '\'' +
                ", owner=" + owner +
                ", created=" + created +
                ", stake=" + stake +
                ", reward=" + reward +
                '}';
    }

    public static void writeObject(ObjectWriter w, NodeInfo n) {
        w.writeListOfNullable(
                n.peer_id,
                n.name,
                n.endpoint,
                n.owner,
                n.created,
                n.stake,
                n.reward
        );
    }

    public static NodeInfo readObject(ObjectReader r) {
        r.beginList();
        NodeInfo n = new NodeInfo(
                r.readString(),
                r.readNullable(String.class),
                r.readString(),
                r.readAddress(),
                r.readLong(),
                r.readNullable(BigInteger.class),
                r.readNullable(BigInteger.class));
        r.end();
        return n;
    }
}
