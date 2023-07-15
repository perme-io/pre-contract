package com.iconloop.score.pds;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import score.Address;

import java.math.BigInteger;
import java.util.Map;


public class PolicyInfo {
    private final String policyId;
    private final JsonObject jsonObject;

    public PolicyInfo(String policy_id) {
        this.policyId = policy_id;
        this.jsonObject = new JsonObject();
    }

    public String getString(String keyName) {
        return this.jsonObject.getString(keyName, "");
    }

    public int getInt(String keyName) {
        return this.jsonObject.getInt(keyName, 0);
    }

    public JsonObject getJsonObject() { return this.jsonObject; }

    public void fromParams(String label_id,
                           String name,
                           String owner,
                           String consumer,
                           BigInteger threshold,
                           BigInteger proxy_number,
                           String[] proxies,
                           String created,
                           String expire_at) {
        String proxiesJsonString = Helper.StringListToJsonString(proxies);
        this.jsonObject.set("policy_id", this.policyId);
        this.jsonObject.set("label_id", (label_id == null) ? this.jsonObject.getString("label_id", "") : label_id);
        this.jsonObject.set("name", (name == null) ? this.jsonObject.getString("name", "") : name);
        this.jsonObject.set("owner", (owner == null) ? this.jsonObject.getString("owner", "") : owner);
        this.jsonObject.set("consumer", (consumer == null) ? this.jsonObject.getString("consumer", "") : consumer);
        this.jsonObject.set("threshold", (threshold.intValue() == 0) ? this.jsonObject.getInt("threshold", 0) : threshold.intValue());
        this.jsonObject.set("proxy_number", (proxy_number.intValue() == 0) ? this.jsonObject.getInt("proxy_number", 0) : proxy_number.intValue());
        this.jsonObject.set("proxies", (proxies == null) ? this.jsonObject.getString("proxies", "") : proxiesJsonString);
        this.jsonObject.set("created", (created.isEmpty()) ? this.jsonObject.getString("created", "") : created);
        this.jsonObject.set("expire_at", (expire_at == null) ? this.jsonObject.getString("expire_at", "") : expire_at);
    }

    public static PolicyInfo fromString(String policy_info) {
        JsonValue jsonValue = Json.parse(policy_info);
        JsonObject json = jsonValue.asObject();
        String policyId = json.getString("policy_id", "");

        PolicyInfo policyInfo = new PolicyInfo(policyId);
        JsonObject jsonObject = policyInfo.getJsonObject();

        jsonObject.set("policy_id", policyId);
        jsonObject.set("label_id", json.getString("label_id", jsonObject.getString("label_id", "")));
        jsonObject.set("name", json.getString("name", jsonObject.getString("name", "")));
        jsonObject.set("owner", json.getString("owner", jsonObject.getString("owner", "")));
        jsonObject.set("consumer", json.getString("consumer", jsonObject.getString("consumer", "")));
        jsonObject.set("threshold", json.getInt("threshold", jsonObject.getInt("threshold", 0)));
        jsonObject.set("proxy_number", json.getInt("proxy_number", jsonObject.getInt("proxy_number", 0)));
        jsonObject.set("proxies", json.getString("proxies", jsonObject.getString("proxies", "")));
        jsonObject.set("created", json.getString("created", jsonObject.getString("created", "")));
        jsonObject.set("expire_at", json.getString("expire_at", jsonObject.getString("expire_at", "")));

        return policyInfo;
    }

    public String toString() {
        return this.jsonObject.toString();
    }

    public String[] proxyList() {
        String proxiesJsonString = this.jsonObject.getString("proxies", "");
        return Helper.JsonStringToStringList("proxies", proxiesJsonString);
    }

    public Map<String, Object> toMap() {
        return Map.ofEntries(
                Map.entry("policy_id", this.jsonObject.getString("policy_id", "")),
                Map.entry("label_id", this.jsonObject.getString("label_id", "")),
                Map.entry("name", this.jsonObject.getString("name", "")),
                Map.entry("owner", this.jsonObject.getString("owner", "")),
                Map.entry("consumer", this.jsonObject.getString("consumer", "")),
                Map.entry("threshold", this.jsonObject.getInt("threshold", 0)),
                Map.entry("proxy_number", this.jsonObject.getInt("proxy_number", 0)),
                Map.entry("proxies", proxyList()),
                Map.entry("created", this.jsonObject.getString("created", "")),
                Map.entry("expire_at", this.jsonObject.getString("expire_at", ""))
        );
    }
}
