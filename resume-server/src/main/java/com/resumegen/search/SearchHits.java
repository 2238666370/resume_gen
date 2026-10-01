package com.resumegen.search;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 检索命中结果：有序 id 列表 + 总数（供分页回源 MySQL）。
 */
@Data
@AllArgsConstructor
public class SearchHits<T> {

    private List<T> ids;
    private long total;
}
