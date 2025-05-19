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

import score.Context;
import score.DictDB;

/**
 * EnumerableMap provides a traditional mapping structure with an additional key-value enumeration
 * that tells you how many items are in the mapping and allows you to iterate over them.
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class EnumerableMap<K, V> {
    private final EnumerableSet<K> keys;
    private final DictDB<K, V> values;

    public EnumerableMap(String id, Class<K> keyClass, Class<V> valueClass) {
        this.keys = new EnumerableSet<>(id + "_keys", keyClass);
        this.values = Context.newDictDB(id + "_values", valueClass);
    }

    public int length() {
        return keys.length();
    }

    public boolean contains(K key) {
        return keys.contains(key);
    }

    public K getKey(int index) {
        return keys.at(index);
    }

    public V get(K key) {
        return values.get(key);
    }

    public V getOrThrow(K key, String msg) {
        var entry = this.get(key);
        if (entry != null) {
            return entry;
        }
        Context.revert(msg);
        return null; // should not reach here, but made compiler happy
    }

    public void set(K key, V value) {
        values.set(key, value);
        keys.add(key);
    }

    public void remove(K key) {
        values.set(key, null);
        keys.remove(key);
    }

    public void removeAll() {
        var last = keys.length() - 1;
        for (int i = last; i >= 0; i--) {
            var key = keys.at(i);
            remove(key);
        }
    }
}
