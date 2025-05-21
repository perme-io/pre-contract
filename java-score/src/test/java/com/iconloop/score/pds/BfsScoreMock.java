package com.iconloop.score.pds;

import com.eclipsesource.json.Json;
import score.Address;
import score.BranchDB;
import score.Context;
import score.DictDB;
import score.annotation.External;
import score.annotation.Optional;

import java.math.BigInteger;

public class BfsScoreMock {
    private final BranchDB<String, DictDB<String, String>> pinInfos = Context.newBranchDB("pinInfos", String.class);
    private final BranchDB<String, DictDB<String, BigInteger>> groupExpires = Context.newBranchDB("groupExpires", BigInteger.class);

    static final class PinInfo {
        private final String cid;
        private final int size;
        private String expireAt;
        private final String group;
        private final String name;

        public PinInfo(String cid, int size, String expireAt, String group, String name) {
            this.cid = cid;
            this.size = size;
            this.expireAt = expireAt;
            this.group = group;
            this.name = name;
        }

        public static PinInfo fromString(String s) {
            if (s == null) {
                return new PinInfo(null, 0, null, null, null);
            }
            var json = Json.parse(s).asObject();
            return new PinInfo(
                    json.get("cid").asString(),
                    json.get("size").asInt(),
                    json.get("expireAt").asString(),
                    json.get("group").asString(),
                    json.get("name").asString()
            );
        }

        @Override
        public String toString() {
            return Json.object()
                    .add("cid", cid)
                    .add("size", size)
                    .add("expireAt", expireAt)
                    .add("group", group)
                    .add("name", name)
                    .toString();
        }
    }

    @External
    public void pin(String cid, int size, BigInteger expire_at,
                    @Optional String group, @Optional String name, @Optional String did_sign) {
        String owner = Context.getCaller().toString();
        Context.require(get_pin(owner, cid) == null, "Already pinned: " + cid);
        Context.require(size > 0, "Invalid size: " + size);
        Context.require(expire_at != null, "Invalid expire_at: " + expire_at);

        var pinInfo = new PinInfo(cid, size, expire_at.toString(), group, name);
        pinInfos.at(owner).set(cid, pinInfo.toString());
    }

    @External(readonly=true)
    public String get_pin(String owner, String cid) {
        var pinInfo = PinInfo.fromString(pinInfos.at(owner).get(cid));
        if (pinInfo.cid != null && pinInfo.group != null) {
            BigInteger groupExpire = get_group(owner, pinInfo.group);
            if (groupExpire.signum() > 0 && groupExpire.compareTo(new BigInteger(pinInfo.expireAt)) < 0) {
                pinInfo.expireAt = groupExpire.toString();
            }
            return pinInfo.toString();
        }
        return null;
    }

    @External
    public void update_group(String group, BigInteger expire_at, @Optional String did_sign) {
        String owner = Context.getCaller().toString();
        groupExpires.at(owner).set(group, expire_at);
    }

    @External(readonly=true)
    public BigInteger get_group(String owner, String group) {
        return this.groupExpires.at(owner).getOrDefault(group, BigInteger.ZERO);
    }
}
