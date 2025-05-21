package com.iconloop.score.pds;

import com.parametacorp.util.EnumerableMap;
import com.parametacorp.util.EnumerableSet;
import score.Context;
import score.DictDB;
import score.ObjectReader;
import score.ObjectWriter;

import java.math.BigInteger;

public class LabelInfo {
    private final String label_id;
    private final String owner;
    private String name;
    private final String public_key;
    private BigInteger expire_at;
    private String category;
    private String producer;
    private BigInteger producer_expire_at;
    private final long created;
    private long last_updated;
    private long revoked;

    private final EnumerableMap<String, DataInfo> dataMap;
    private final EnumerableSet<String> policyIds;

    public LabelInfo(Builder builder) {
        this.label_id = builder.labelId;
        this.owner = builder.owner;
        this.name = builder.name;
        this.public_key = builder.publicKey;
        this.expire_at = builder.expireAt;
        this.category = builder.category;
        this.producer = builder.producer;
        this.producer_expire_at = builder.producerExpireAt;
        this.created = builder.created;
        this.last_updated = Math.max(builder.lastUpdated, created);

        this.dataMap = new EnumerableMap<>(label_id, String.class, DataInfo.class);
        this.policyIds = new EnumerableSet<>(label_id, String.class);
    }

    public String getLabel_id() {
        return label_id;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getPublic_key() {
        return public_key;
    }

    public BigInteger getExpire_at() {
        return expire_at;
    }

    public String getCategory() {
        return category;
    }

    public String getProducer() {
        return producer;
    }

    public BigInteger getProducer_expire_at() {
        return producer_expire_at;
    }

    public long getCreated() {
        return created;
    }

    public long getLast_updated() {
        return last_updated;
    }

    @Override
    public String toString() {
        return "LabelInfo{" +
                "label_id='" + label_id + '\'' +
                ", owner='" + owner + '\'' +
                ", name='" + name + '\'' +
                ", public_key='" + public_key + '\'' +
                ", expire_at=" + expire_at +
                ", category='" + category + '\'' +
                ", producer='" + producer + '\'' +
                ", producer_expire_at=" + producer_expire_at +
                ", created=" + created +
                ", last_updated=" + last_updated +
                ", revoked=" + revoked +
                '}';
    }

    public static void writeObject(ObjectWriter w, LabelInfo l) {
        w.writeListOfNullable(
                l.label_id,
                l.owner,
                l.name,
                l.public_key,
                l.expire_at,
                l.category,
                l.producer,
                l.producer_expire_at,
                l.created,
                l.last_updated,
                l.revoked);
    }

    public static LabelInfo readObject(ObjectReader r) {
        r.beginList();
        LabelInfo l = new Builder()
                .labelId(r.readString())
                .owner(r.readString())
                .name(r.readString())
                .publicKey(r.readString())
                .expireAt(r.readBigInteger())
                .category(r.readNullable(String.class))
                .producer(r.readString())
                .producerExpireAt(r.readBigInteger())
                .created(r.readLong())
                .lastUpdated(r.readLong())
                .build();
        l.revoked = r.readLong();
        r.end();
        return l;
    }

    public void revoke(long height) {
        this.revoked = height;
        this.last_updated = height;
    }

    public boolean isRevoked() {
        return this.revoked > 0;
    }

    public void update(Builder attrs) {
        if (attrs.name != null) {
            this.name = attrs.name;
        }
        if (attrs.expireAt != null) {
            this.expire_at = attrs.expireAt;
        }
        if (attrs.category != null) {
            this.category = attrs.category;
        }
        if (attrs.producer != null) {
            this.producer = attrs.producer;
        }
        if (attrs.producerExpireAt != null) {
            this.producer_expire_at = attrs.producerExpireAt;
        }
        this.last_updated = attrs.lastUpdated;
    }

    public void checkOwnerOrThrow(String owner) {
        Context.require(this.owner.equals(owner), "invalid owner");
    }

    public boolean addData(DataInfo dataInfo) {
        var dataId = dataInfo.getData();
        // check duplicate first
        if (dataMap.get(dataId) != null) {
            return false;
        }
        dataMap.set(dataId, dataInfo);
        return true;
    }

    public int removeDataAll() {
        int size = dataMap.length();
        dataMap.removeAll();
        return size;
    }

    private static final int DEFAULT_PAGE_SIZE = 25;

    private int getStart(int offset, int total) {
        int start = Math.min(offset, total - 1);
        if (start < 0) {
            start = total + start;
            if (start < 0) {
                start = 0;
            }
        }
        return start;
    }

    private int getSize(int start, int limit, int total) {
        int size = (limit > 0) ? limit : DEFAULT_PAGE_SIZE;
        return Math.min(size, total - start);
    }

    public PageOfData getDataPage(int offset, int limit) {
        int total = dataMap.length();
        if (total == 0) {
            return new PageOfData(0, 0, 0, new DataInfo[0]);
        }
        int start = getStart(offset, total);
        int size = getSize(start, limit, total);
        DataInfo[] infos = new DataInfo[size];
        for (int i = 0; i < size; i++) {
            var key = dataMap.getKey(start + i);
            infos[i] = dataMap.get(key);
        }
        return new PageOfData(start, size, total, infos);
    }

    public void addPolicyId(String policyId) {
        policyIds.add(policyId);
    }

    public int removePolicyAll(DictDB<String, PolicyInfo> policyInfo) {
        var size = policyIds.length();
        for (int i = size - 1; i >= 0; i--) {
            var key = policyIds.at(i);
            policyIds.remove(key);
            policyInfo.set(key, null);
        }
        return size;
    }

    public PageOfPolicy getPoliciesPage(DictDB<String, PolicyInfo> policyMap, int offset, int limit) {
        int total = policyIds.length();
        if (total == 0) {
            return new PageOfPolicy(0, 0, 0, new PolicyInfo[0]);
        }
        int start = getStart(offset, total);
        int size = getSize(start, limit, total);
        PolicyInfo[] infos = new PolicyInfo[size];
        for (int i = 0; i < size; i++) {
            var key = policyIds.at(start + i);
            infos[i] = policyMap.get(key);
        }
        return new PageOfPolicy(start, size, total, infos);
    }

    public static class Builder {
        private String labelId;
        private String owner;
        private String name;
        private String publicKey;
        private BigInteger expireAt;
        private String category;
        private String producer;
        private BigInteger producerExpireAt;
        private long created;
        private long lastUpdated;

        public Builder labelId(String labelId) {
            this.labelId = labelId;
            return this;
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder publicKey(String publicKey) {
            this.publicKey = publicKey;
            return this;
        }

        public Builder expireAt(BigInteger expireAt) {
            this.expireAt = expireAt;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder producer(String producer) {
            this.producer = producer;
            return this;
        }

        public Builder producerExpireAt(BigInteger producerExpireAt) {
            this.producerExpireAt = producerExpireAt;
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

        public LabelInfo build() {
            return new LabelInfo(this);
        }
    }
}
