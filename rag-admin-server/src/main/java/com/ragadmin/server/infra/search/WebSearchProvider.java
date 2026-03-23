package com.ragadmin.server.infra.search;

import java.util.List;

/**
 * 联网搜索提供方抽象。
 * 当前阶段先提供统一接口，后续可替换为真实搜索供应商实现。
 */
public interface WebSearchProvider {

    List<WebSearchSnippet> search(String query, int topK);

    /**
     * 标记当前搜索提供方是否真正可用，便于前后端统一收口联网能力展示。
     */
    default boolean isAvailable() {
        return true;
    }
}
