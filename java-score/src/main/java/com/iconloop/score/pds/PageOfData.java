package com.iconloop.score.pds;

public class PageOfData {
    private final int offset;
    private final int size;
    private final int total;
    private final DataInfo[] ids;

    public PageOfData(int offset, int size, int total, DataInfo[] ids) {
        this.offset = offset;
        this.size = size;
        this.total = total;
        this.ids = ids;
    }

    public int getOffset() {
        return offset;
    }

    public int getSize() {
        return size;
    }

    public int getTotal() {
        return total;
    }

    public DataInfo[] getIds() {
        return ids;
    }
}
