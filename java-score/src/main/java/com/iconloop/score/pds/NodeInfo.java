package com.iconloop.score.pds;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import score.Address;

import java.math.BigInteger;
import java.util.Map;


public class NodeInfo {
    private final String peerId;
    private final JsonObject jsonObject;

    public NodeInfo(String peer_id) {
        this.peerId = peer_id;
        this.jsonObject = new JsonObject();
    }

    public String getString(String keyName) {
        return this.jsonObject.getString(keyName, "");
    }

    public JsonObject getJsonObject() { return this.jsonObject; }

    public void fromParams(String endpoint,
                           String name,
                           String comment,
                           String created,
                           Address owner,
                           BigInteger stake,
                           BigInteger reward) {
        this.jsonObject.set("peer_id", this.peerId);
        this.jsonObject.set("endpoint", (endpoint == null) ? this.jsonObject.getString("endpoint", "") : endpoint);
        this.jsonObject.set("name", (name == null) ? this.jsonObject.getString("name", "") : name);
        this.jsonObject.set("comment", (comment == null) ? this.jsonObject.getString("comment", "") : comment);
        this.jsonObject.set("created", (created.isEmpty()) ? this.jsonObject.getString("created", "") : created);
        this.jsonObject.set("owner", (owner == null) ? this.jsonObject.getString("owner", "") : owner.toString());
        this.jsonObject.set("stake", (stake.intValue() == 0) ? this.jsonObject.getString("stake", "0") : stake.toString(16));
        this.jsonObject.set("reward", (reward.intValue() == 0) ? this.jsonObject.getString("reward", "0") : reward.toString(16));
    }

    public static NodeInfo fromString(String node_info) {
        JsonValue jsonValue = Json.parse(node_info);
        JsonObject json = jsonValue.asObject();
        String peerId = json.getString("peer_id", "");

        NodeInfo nodeInfo = new NodeInfo(peerId);
        JsonObject jsonObject = nodeInfo.getJsonObject();

        jsonObject.set("peer_id", peerId);
        jsonObject.set("endpoint", json.getString("endpoint", jsonObject.getString("endpoint", "")));
        jsonObject.set("name", json.getString("name", jsonObject.getString("name", "")));
        jsonObject.set("comment", json.getString("comment", jsonObject.getString("comment", "")));
        jsonObject.set("created", json.getString("created", jsonObject.getString("created", "")));
        jsonObject.set("owner", json.getString("owner", jsonObject.getString("owner", "")));
        jsonObject.set("stake", json.getString("stake", jsonObject.getString("stake", "0")));
        jsonObject.set("reward", json.getString("reward", jsonObject.getString("reward", "0")));

        return nodeInfo;
    }

    public String toString() {
        return this.jsonObject.toString();
    }

    public Map<String, Object> toMap(int nodeCount) {
        return Map.ofEntries(
                Map.entry("peer_id", this.jsonObject.getString("peer_id", "")),
                Map.entry("endpoint", this.jsonObject.getString("endpoint", "")),
                Map.entry("name", this.jsonObject.getString("name", "")),
                Map.entry("comment", this.jsonObject.getString("comment", "")),
                Map.entry("created", this.jsonObject.getString("created", "")),
                Map.entry("owner", this.jsonObject.getString("owner", "")),
                Map.entry("stake", "0x" + this.jsonObject.getString("stake", "0")),
                Map.entry("reward", "0x" + this.jsonObject.getString("reward", "0"))
        );
    }
}
