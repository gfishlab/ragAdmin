package com.ragadmin.server.infra.ai.chat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rag.ai.chat.answer-metadata")
public class ChatAnswerMetadataProperties {

    /**
     * 是否启用基于模型的回答后处理元数据生成。
     * 关闭后统一回退到规则结果，便于快速止血。
     */
    private boolean enabled = true;

    /**
     * 是否记录元数据结果日志。
     */
    private boolean logMetadata = true;
}
