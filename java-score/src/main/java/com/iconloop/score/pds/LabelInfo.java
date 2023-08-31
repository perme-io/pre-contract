package com.iconloop.score.pds;

import score.Context;
import score.ObjectReader;
import score.ObjectWriter;

import java.math.BigInteger;
import java.util.Map;


public class LabelInfo {
    private final String labelId;
    private String name;
    private String owner;
    private String producer;
    private String producerExpireAt;
    private String capsule;
    private String data;
    private String[] policies;
    private String created;
    private String expireAt;
    private BigInteger lastUpdated;


    public LabelInfo(String labelId,
                     String name,
                     String owner,
                     String producer,
                     String producerExpireAt,
                     String capsule,
                     String data,
                     String[] policies,
                     String created,
                     String expireAt,
                     BigInteger lastUpdated) {
        this.labelId = labelId;
        this.name = name;
        this.owner = owner;
        this.producer = producer;
        this.producerExpireAt = producerExpireAt;
        this.capsule = capsule;
        this.data = data;
        this.policies = policies;
        this.created = created;
        this.expireAt = expireAt;
        this.lastUpdated = (lastUpdated != null) ? lastUpdated : new BigInteger(String.valueOf(Context.getBlockTimestamp()));
    }

    public void update(String name,
                       String owner,
                       String producer,
                       String producerExpireAt,
                       String capsule,
                       String data,
                       String[] policies,
                       String created,
                       String expireAt) {
        this.name = (name == null) ? this.name : name;
        this.owner = (owner == null) ? this.owner : owner;
        this.producer = (producer == null) ? this.producer : producer;
        this.producerExpireAt = (producerExpireAt == null) ? this.producerExpireAt : producerExpireAt;
        this.capsule = (capsule == null) ? this.capsule : capsule;
        this.data = (data == null) ? this.data : data;
        this.policies = (policies == null) ? this.policies : policies;
        this.created = (created == null) ? this.created : created;
        this.expireAt = (expireAt == null) ? this.expireAt : expireAt;
        this.lastUpdated = new BigInteger(String.valueOf(Context.getBlockTimestamp()));
    }

    public boolean checkOwner(String owner) {
        return this.owner.equals(owner);
    }

    public String getProducer() {
        return this.producer;
    }

    public void setPolicies(String[] policies) {
        this.policies = policies;
    }

    public String[] getPolicies() {
        return this.policies;
    }

    public BigInteger getExpireAt() {
        return (this.expireAt.isEmpty()) ? BigInteger.ZERO : new BigInteger(this.expireAt);
    }

    public BigInteger getLastUpdated() {
        return this.lastUpdated;
    }

    public boolean checkLastUpdated(BigInteger lastUpdated) {
        return this.lastUpdated.equals(lastUpdated);
    }

    public static void writeObject(ObjectWriter w, LabelInfo t) {
        String policiesJsonString = Helper.StringListToJsonString(t.policies);
        w.beginList(11);
        w.writeNullable(t.labelId);
        w.writeNullable(t.name);
        w.writeNullable(t.owner);
        w.writeNullable(t.producer);
        w.writeNullable(t.producerExpireAt);
        w.writeNullable(t.capsule);
        w.writeNullable(t.data);
        w.writeNullable(policiesJsonString);
        w.writeNullable(t.created);
        w.writeNullable(t.expireAt);
        w.write(t.lastUpdated);
        w.end();
    }

    public static LabelInfo readObject(ObjectReader r) {
        r.beginList();
        LabelInfo t = new LabelInfo(
                r.readNullable(String.class),
                r.readNullable(String.class),
                r.readNullable(String.class),
                r.readNullable(String.class),
                r.readNullable(String.class),
                r.readNullable(String.class),
                r.readNullable(String.class),
                Helper.JsonStringToStringList("policies", r.readNullable(String.class)),
                r.readNullable(String.class),
                r.readNullable(String.class),
                r.readBigInteger());
        r.end();
        return t;
    }

    public Map<String, Object> toMap() {
        return Map.ofEntries(
                Map.entry("label_id", this.labelId),
                Map.entry("name", this.name),
                Map.entry("owner", this.owner),
                Map.entry("producer", this.producer),
                Map.entry("producer_expire_at", this.producerExpireAt),
                Map.entry("capsule", this.capsule),
                Map.entry("data", this.data),
                Map.entry("policies", this.policies),
                Map.entry("created", this.created),
                Map.entry("expire_at", this.expireAt),
                Map.entry("last_updated", this.lastUpdated)
        );
    }
}
