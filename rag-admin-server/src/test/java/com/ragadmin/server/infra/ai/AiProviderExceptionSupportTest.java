package com.ragadmin.server.infra.ai;

import com.ragadmin.server.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProviderExceptionSupportTest {

    @Test
    void shouldTranslateArrearageExceptionToFriendlyMessage() {
        RuntimeException exception = new RuntimeException("""
                400 - {"code":"Arrearage","message":"Access denied, please make sure your account is in good standing. For details, see: https://help.aliyun.com/zh/model-studio/error-code#overdue-payment"}
                """);

        BusinessException translated = AiProviderExceptionSupport.toBusinessException(
                exception,
                "CHAT_PROVIDER_UNAVAILABLE",
                "聊天模型"
        );

        assertTrue(AiProviderExceptionSupport.isProviderAccountIssue(exception));
        assertEquals("CHAT_PROVIDER_UNAVAILABLE", translated.getCode());
        assertEquals(HttpStatus.BAD_GATEWAY, translated.getHttpStatus());
        assertEquals("当前聊天模型提供方账户可能已欠费或额度异常，请联系管理员处理后重试。", translated.getMessage());
    }

    @Test
    void shouldKeepBusinessExceptionMessageUntouched() {
        BusinessException exception = new BusinessException(
                "CHAT_SESSION_NOT_FOUND",
                "会话不存在",
                HttpStatus.NOT_FOUND
        );

        BusinessException translated = AiProviderExceptionSupport.toBusinessException(
                exception,
                "IGNORED",
                "聊天模型"
        );

        assertEquals(exception, translated);
        assertEquals("会话不存在", AiProviderExceptionSupport.resolveUserMessage(exception, "聊天模型"));
    }
}
