package com.iconloop.score.pds;

import score.ObjectReader;
import score.ObjectWriter;

import java.math.BigInteger;

public class DataInfo {
    private final String data_id;
    private final String name;
    private final BigInteger size;

    public DataInfo(String dataId, String name, BigInteger size) {
        this.data_id = dataId;
        this.name = name;
        this.size = size;
    }

    public String getData_id() {
        return data_id;
    }

    public String getName() {
        return name;
    }

    public BigInteger getSize() {
        return size;
    }

    @Override
    public String toString() {
        return "DataInfo{" +
                "data_id='" + data_id + '\'' +
                ", name='" + name + '\'' +
                ", size=" + size +
                '}';
    }

    public static void writeObject(ObjectWriter w, DataInfo d) {
        w.writeListOf(d.data_id, d.name, d.size);
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
