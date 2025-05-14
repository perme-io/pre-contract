/*
 * Copyright 2024 PARAMETA Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.parametacorp.jwt;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import score.Context;
import scorex.util.Base64;
import scorex.util.StringTokenizer;

public class Jwt {
    static final String ALGORITHM_ES256K = "ES256K";

    private final String header;
    private final String payload;
    private final String sig;
    private byte[] msgHash;

    public Jwt(String jwt) {
        String[] tokens = new String[3];
        StringTokenizer tokenizer = new StringTokenizer(jwt, ".");
        for (int i = 0; i < tokens.length; i++) {
            Context.require(tokenizer.hasMoreTokens(), "need more tokens");
            tokens[i] = tokenizer.nextToken();
        }
        Context.require(!tokenizer.hasMoreTokens(), "should be no more tokens");

        // just hold each part as is so that it can be used for verifying later
        // it will be decoded on demand
        this.header = tokens[0];
        this.payload = tokens[1];
        this.sig = tokens[2];
    }

    public JsonObject getHeader() {
        return Json.parse(new String(Base64.getUrlDecoder().decode(header.getBytes()))).asObject();
    }

    public JsonObject getPayload() {
        return Json.parse(new String(Base64.getUrlDecoder().decode(payload.getBytes()))).asObject();
    }

    public byte[] getSig() {
        return Base64.getUrlDecoder().decode(sig.getBytes());
    }

    public byte[] getHash() {
        if (msgHash == null) {
            String content = header + "." + payload;
            msgHash = Context.hash("sha-256", content.getBytes());
        }
        return msgHash;
    }

    public String[] parseHeader() {
        JsonObject obj = getHeader();
        Context.require(obj.size() == 2, "invalid header");
        JsonValue alg = obj.get("alg");
        Context.require(alg != null && ALGORITHM_ES256K.equals(alg.asString()), "invalid algorithm specified");
        JsonValue kid = obj.get("kid");
        Context.require(kid != null, "kid not found");

        String[] tokens = new String[2];
        StringTokenizer tokenizer = new StringTokenizer(kid.asString(), "#");
        for (int i = 0; i < tokens.length; i++) {
            Context.require(tokenizer.hasMoreTokens(), "need more tokens");
            tokens[i] = tokenizer.nextToken();
        }
        Context.require(!tokenizer.hasMoreTokens(), "should be no more tokens");
        return tokens;
    }

    public boolean verify(byte[] pubKey) {
        return Context.verifySignature("ecdsa-secp256k1", getHash(), getSig(), pubKey);
    }
}
