package com.iconloop.score.pds;

import score.ObjectReader;
import score.ObjectWriter;

import java.math.BigInteger;
import java.util.Map;


public class PolicyInfo {
    private final String policyId;
    private final String labelId;
    private final String name;
    private final String owner;
    private final String consumer;
    private final BigInteger threshold;
    private final BigInteger proxyNumber;
    private final String[] proxies;
    private final String created;
    private final String expireAt;

    public PolicyInfo(String policyId,
                      String labelId,
                      String name,
                      String owner,
                      String consumer,
                      BigInteger threshold,
                      BigInteger proxy_number,
                      String[] proxies,
                      String created,
                      String expire_at) {
        this.policyId = policyId;
        this.labelId = labelId;
        this.name = name;
        this.owner = owner;
        this.consumer = consumer;
        this.threshold = threshold;
        this.proxyNumber = proxy_number;
        this.proxies = proxies;
        this.created = created;
        this.expireAt = expire_at;
    }

    public boolean checkOwner(String owner) {
        return this.owner.equals(owner);
    }

    public String getLabelId() {
        return labelId;
    }

    public String getName() {
        return name;
    }

    public String getConsumer() {
        return consumer;
    }

    public String getExpireAt() {
        return expireAt;
    }

    public static void writeObject(ObjectWriter w, PolicyInfo t) {
        String proxiesJsonString = Helper.StringListToJsonString(t.proxies);
        w.beginList(10);
        w.writeNullable(t.policyId);
        w.writeNullable(t.labelId);
        w.writeNullable(t.name);
        w.writeNullable(t.owner);
        w.writeNullable(t.consumer);
        w.writeNullable(t.threshold);
        w.writeNullable(t.proxyNumber);
        w.writeNullable(proxiesJsonString);
        w.writeNullable(t.created);
        w.writeNullable(t.expireAt);
        w.end();
    }

    public static PolicyInfo readObject(ObjectReader r) {
        r.beginList();
        PolicyInfo t = new PolicyInfo(
                r.readNullable(String.class),
                r.readNullable(String.class),
                r.readNullable(String.class),
                r.readNullable(String.class),
                r.readNullable(String.class),
                r.readNullable(BigInteger.class),
                r.readNullable(BigInteger.class),
                Helper.JsonStringToStringList("proxies", r.readNullable(String.class)),
                r.readNullable(String.class),
                r.readNullable(String.class));
        r.end();
        return t;
    }

    public Map<String, Object> toMap() {
        return Map.ofEntries(
                Map.entry("policy_id", this.policyId),
                Map.entry("label_id", this.labelId),
                Map.entry("name", this.name),
                Map.entry("owner", this.owner),
                Map.entry("consumer", this.consumer),
                Map.entry("threshold", this.threshold),
                Map.entry("proxy_number", this.proxyNumber),
                Map.entry("proxies", this.proxies),
                Map.entry("created", this.created),
                Map.entry("expireAt", this.expireAt)
        );
    }
}
