package com.iconloop.score.pds;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;

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
}
