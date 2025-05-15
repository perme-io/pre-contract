package com.iconloop.score.pds;

import score.ObjectReader;
import score.ObjectWriter;

import java.math.BigInteger;

public class DataInfo {
    private final String data;
    private final String name;
    private final BigInteger size;

    public DataInfo(String data, String name, BigInteger size) {
        this.data = data;
        this.name = name;
        this.size = size;
    }

    public String getData() {
        return data;
    }

    public String getName() {
        return name;
    }

    public BigInteger getSize() {
        return size;
    }

    public static void writeObject(ObjectWriter w, DataInfo d) {
        w.writeListOf(d.data, d.name, d.size);
    }

    public static DataInfo readObject(ObjectReader r) {
        r.beginList();
        DataInfo d = new DataInfo(
                r.readString(),
                r.readString(),
                r.readBigInteger());
        r.end();
        return d;
    }
}
