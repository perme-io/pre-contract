package com.iconloop.score.pds;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

import java.util.StringTokenizer;


public class Helper {
    public static String StringListToJsonString(String[] list) {
        if(list == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for(String s : list) {
            if(builder.length() > 0) {
                builder.append(",");
            }
            builder.append("\"").append(s).append("\"");
        }

        return builder.toString();
    }

    public static String[] JsonStringToStringList(String keyName, String jsonString) {
        String json_str = "{\"" + keyName + "\":[" + jsonString + "]}";
        JsonValue jsonValue = Json.parse(json_str);
        JsonObject json = jsonValue.asObject();
        JsonArray jsonArray = json.get(keyName).asArray();

        int index = 0;
        String[] stringList = new String[jsonArray.size()];
        for(JsonValue value:jsonArray){
            stringList[index++] = value.toString().replace("\"", "");
        }

        return stringList;
    }

    public static DidMessage DidMessageParser(String message) {
        String[] did_info = new String[4];
        StringTokenizer st = new StringTokenizer(message, "#");
        int countTokens = st.countTokens();

        int index = 0;
        while (st.hasMoreTokens()) {
            did_info[index++] = st.nextToken();
        }

        String did = message;
        String kid = "publicKey";
        String target = "";
        String nonce = "0";

        if (countTokens >= 2) {
            did = did_info[0];
            kid = did_info[1];
        }

        if (countTokens == 4) {
            target = did_info[2];
            nonce = did_info[3];
        }

        return new DidMessage(did, kid, target, Integer.parseInt(nonce));
    }
}
