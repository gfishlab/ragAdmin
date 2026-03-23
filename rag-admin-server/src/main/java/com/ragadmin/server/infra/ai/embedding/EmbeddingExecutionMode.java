package com.ragadmin.server.infra.ai.embedding;

/**
 * 向量模型的调用模式需要与上层编排解耦，便于后续在不改业务主链路的情况下扩展异步批量模型。
 */
public enum EmbeddingExecutionMode {

    /**
     * 直接按文本列表同步返回向量，适用于当前知识库切片与检索链路。
     */
    SYNC_TEXT,

    /**
     * 通过文件或对象地址提交异步批量任务，后续需要轮询或回调获取结果。
     */
    ASYNC_BATCH
}
