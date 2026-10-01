package com.resumegen.common;

import lombok.Getter;

import java.util.List;

@Getter
public class PageResult<T> {

    private final List<T> records;
    private final long total;
    private final long page;
    private final long size;

    public PageResult(List<T> records, long total, long page, long size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
    }
}