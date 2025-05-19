package com.iconloop.score.pds;

import score.ObjectReader;
import score.ObjectWriter;

import java.math.BigInteger;

public class PolicyInfo {
    private final String policy_id;
    private final String label_id;
    private final String name;
    private final String consumer;
    private final BigInteger threshold;
    private BigInteger expire_at;
    private final long created;
    private long last_updated;

    public PolicyInfo(Builder builder) {
        this.policy_id = builder.policyId;
        this.label_id = builder.labelId;
        this.name = builder.name;
        this.consumer = builder.consumer;
        this.threshold = builder.threshold;
        this.expire_at = builder.expireAt;
        this.created = builder.created;
        this.last_updated = Math.max(builder.lastUpdated, created);
    }

    public String getPolicy_id() {
        return policy_id;
    }

    public String getLabel_id() {
        return label_id;
    }

    public String getName() {
        return name;
    }

    public String getConsumer() {
        return consumer;
    }

    public BigInteger getThreshold() {
        return threshold;
    }

    public BigInteger getExpire_at() {
        return expire_at;
    }

    public long getCreated() {
        return created;
    }

    public long getLast_updated() {
        return last_updated;
    }

    @Override
    public String toString() {
        return "PolicyInfo{" +
                "policy_id='" + policy_id + '\'' +
                ", label_id='" + label_id + '\'' +
                ", name='" + name + '\'' +
                ", consumer='" + consumer + '\'' +
                ", threshold=" + threshold +
                ", expire_at=" + expire_at +
                ", created=" + created +
                ", last_updated=" + last_updated +
                '}';
    }

    public static void writeObject(ObjectWriter w, PolicyInfo p) {
        w.writeListOf(
                p.policy_id,
                p.label_id,
                p.name,
                p.consumer,
                p.threshold,
                p.expire_at,
                p.created,
                p.last_updated);
    }

    public static PolicyInfo readObject(ObjectReader r) {
        r.beginList();
        PolicyInfo p = new Builder()
                .policyId(r.readString())
                .labelId(r.readString())
                .name(r.readString())
                .consumer(r.readString())
                .threshold(r.readBigInteger())
                .expireAt(r.readBigInteger())
                .created(r.readLong())
                .lastUpdated(r.readLong())
                .build();
        r.end();
        return p;
    }

    public void update(Builder attrs) {
        if (attrs.expireAt != null) {
            this.expire_at = attrs.expireAt;
        }
        this.last_updated = attrs.lastUpdated;
    }

    public static class Builder {
        private String policyId;
        private String labelId;
        private String name;
        private String consumer;
        private BigInteger threshold;
        private BigInteger expireAt;
        private long created;
        private long lastUpdated;

        public Builder policyId(String policyId) {
            this.policyId = policyId;
            return this;
        }

        public Builder labelId(String labelId) {
            this.labelId = labelId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder consumer(String consumer) {
            this.consumer = consumer;
            return this;
        }

        public Builder threshold(BigInteger threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder expireAt(BigInteger expireAt) {
            this.expireAt = expireAt;
            return this;
        }

        public Builder created(long created) {
            this.created = created;
            return this;
        }

        public Builder lastUpdated(long lastUpdated) {
            this.lastUpdated = lastUpdated;
            return this;
        }

        public PolicyInfo build() {
            return new PolicyInfo(this);
        }
    }
}
