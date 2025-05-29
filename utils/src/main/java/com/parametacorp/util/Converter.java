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

package com.parametacorp.util;

public class Converter {
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        return (bytes != null)
                ? bytesToHex(bytes, 0, bytes.length)
                : null;
    }

    public static String bytesToHex(byte[] bytes, int offset, int length) {
        if (bytes == null) {
            return null;
        }
        char[] hexChars = new char[length * 2];
        for (int i = 0; i < length; i++) {
            int v = bytes[i + offset] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexToBytes(String value) {
        if (value == null || (value.length() % 2 != 0)) {
            throw new IllegalArgumentException("Invalid hex value");
        }
        if (value.startsWith("0x")) {
            value = value.substring(2);
        }
        int len = value.length() / 2;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            int j = i * 2;
            bytes[i] = (byte) Integer.parseInt(value.substring(j, j + 2), 16);
        }
        return bytes;
    }
}
