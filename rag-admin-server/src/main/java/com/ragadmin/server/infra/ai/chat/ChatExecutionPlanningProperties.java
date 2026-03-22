package com.ragadmin.server.infra.ai.chat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rag.ai.chat.execution-planning")
public class ChatExecutionPlanningProperties {

    /**
     * 是否启用基于模型的问答前置规划。
     * 关闭后统一回退到规则规划，便于线上快速止血。
     */
    private boolean enabled = true;

    /**
     * 是否记录规划结果日志。
     * 默认开启，便于排查为什么本次请求触发了检索或联网。
     */
    private boolean logPlan = true;
}
