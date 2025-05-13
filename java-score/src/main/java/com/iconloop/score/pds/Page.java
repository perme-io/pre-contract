package com.iconloop.score.pds;

import java.util.List;

public class Page<T> {
    public final int offset;
    public final int size;
    public final int total;
    public final List<T> ids;

    public Page(int offset, int size, int total, List<T> ids) {
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

    public List<T> getIds() {
        return ids;
    }
}
