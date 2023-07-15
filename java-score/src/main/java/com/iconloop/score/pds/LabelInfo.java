package com.iconloop.score.pds;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import score.Address;

import java.util.Map;


public class LabelInfo {
    private final String labelId;
    private final JsonObject jsonObject;

    public LabelInfo(String label_id) {
        this.labelId = label_id;
        this.jsonObject = new JsonObject();
    }

    public String getString(String keyName) {
        return this.jsonObject.getString(keyName, "");
    }

    public int getInt(String keyName) {
        return this.jsonObject.getInt(keyName, 0);
    }

    public JsonObject getJsonObject() { return this.jsonObject; }

    public void fromParams(String name,
                           String owner,
                           String producer,
                           String producer_expire_at,
                           String capsule,
                           String data,
                           String data_updated,
                           String[] policies,
                           String created,
                           String expire_at) {
        String policiesJsonString = Helper.StringListToJsonString(policies);
        this.jsonObject.set("label_id", this.labelId);
        this.jsonObject.set("name", (name == null) ? this.jsonObject.getString("name", "") : name);
        this.jsonObject.set("owner", (owner == null) ? this.jsonObject.getString("owner", "") : owner);
        this.jsonObject.set("producer", (producer == null) ? this.jsonObject.getString("producer", "") : producer);
        this.jsonObject.set("producer_expire_at", (producer_expire_at == null) ? this.jsonObject.getString("producer_expire_at", "") : producer_expire_at);
        this.jsonObject.set("capsule", (capsule == null) ? this.jsonObject.getString("capsule", "") : capsule);
        this.jsonObject.set("data", (data == null) ? this.jsonObject.getString("data", "") : data);
        this.jsonObject.set("data_updated", (data_updated == null) ? this.jsonObject.getString("data_updated", "") : data_updated);
        this.jsonObject.set("policies", (policies == null) ? this.jsonObject.getString("policies", "") : policiesJsonString);
        this.jsonObject.set("created", (created.isEmpty()) ? this.jsonObject.getString("created", "") : created);
        this.jsonObject.set("expire_at", (expire_at == null) ? this.jsonObject.getString("expire_at", "") : expire_at);
    }

    public static LabelInfo fromString(String label_info) {
        JsonValue jsonValue = Json.parse(label_info);
        JsonObject json = jsonValue.asObject();
        String labelId = json.getString("label_id", "");

        LabelInfo labelInfo = new LabelInfo(labelId);
        JsonObject jsonObject = labelInfo.getJsonObject();

        jsonObject.set("label_id", labelId);
        jsonObject.set("name", json.getString("name", jsonObject.getString("name", "")));
        jsonObject.set("owner", json.getString("owner", jsonObject.getString("owner", "")));
        jsonObject.set("producer", json.getString("producer", jsonObject.getString("producer", "")));
        jsonObject.set("producer_expire_at", json.getString("producer_expire_at", jsonObject.getString("producer_expire_at", "")));
        jsonObject.set("capsule", json.getString("capsule", jsonObject.getString("capsule", "")));
        jsonObject.set("data", json.getString("data", jsonObject.getString("data", "")));
        jsonObject.set("data_updated", json.getString("data_updated", jsonObject.getString("data_updated", "")));
        jsonObject.set("policies", json.getString("policies", jsonObject.getString("policies", "")));
        jsonObject.set("created", json.getString("created", jsonObject.getString("created", "")));
        jsonObject.set("expire_at", json.getString("expire_at", jsonObject.getString("expire_at", "")));

        return labelInfo;
    }

    public String toString() {
        return this.jsonObject.toString();
    }

    public String[] policyList() {
        String policyJsonString = this.jsonObject.getString("policies", "");
        return Helper.JsonStringToStringList("policies", policyJsonString);
    }

    public Map<String, Object> toMap() {
        return Map.ofEntries(
                Map.entry("label_id", this.jsonObject.getString("label_id", "")),
                Map.entry("name", this.jsonObject.getString("name", "")),
                Map.entry("owner", this.jsonObject.getString("owner", "")),
                Map.entry("producer", this.jsonObject.getString("producer", "")),
                Map.entry("producer_expire_at", this.jsonObject.getString("producer_expire_at", "")),
                Map.entry("capsule", this.jsonObject.getString("capsule", "")),
                Map.entry("data", this.jsonObject.getString("data", "")),
                Map.entry("data_updated", this.jsonObject.getString("data_updated", "")),
                Map.entry("policies", policyList()),
                Map.entry("created", this.jsonObject.getString("created", "")),
                Map.entry("expire_at", this.jsonObject.getString("expire_at", ""))
        );
    }
}
