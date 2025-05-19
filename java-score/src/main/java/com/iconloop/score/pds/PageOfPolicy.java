package com.iconloop.score.pds;

public class PageOfPolicy {
    private final int offset;
    private final int size;
    private final int total;
    private final PolicyInfo[] ids;

    public PageOfPolicy(int offset, int size, int total, PolicyInfo[] ids) {
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

    public PolicyInfo[] getIds() {
        return ids;
    }
}
