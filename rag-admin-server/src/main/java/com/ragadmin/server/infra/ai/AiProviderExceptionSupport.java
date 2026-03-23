package com.ragadmin.server.infra.ai;

import com.ragadmin.server.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 统一收口模型供应商异常，避免将底层 SDK/HTTP 原始报文直接透传到业务层和前端。
 */
public final class AiProviderExceptionSupport {

    private static final Set<String> ACCOUNT_ISSUE_MARKERS = Set.of(
            "arrearage",
            "overdue-payment",
            "good standing",
            "access denied, please make sure your account is in good standing"
    );

    private AiProviderExceptionSupport() {
    }

    public static BusinessException toBusinessException(Throwable ex, String code, String targetLabel) {
        if (ex instanceof BusinessException businessException) {
            return businessException;
        }
        return new BusinessException(code, resolveUserMessage(ex, targetLabel), HttpStatus.BAD_GATEWAY);
    }

    public static String resolveUserMessage(Throwable ex, String targetLabel) {
        if (ex instanceof BusinessException businessException && StringUtils.hasText(businessException.getMessage())) {
            return businessException.getMessage();
        }
        if (isProviderAccountIssue(ex)) {
            return "当前" + targetLabel + "提供方账户可能已欠费或额度异常，请联系管理员处理后重试。";
        }
        return "当前" + targetLabel + "暂时不可用，请稍后重试。";
    }

    public static boolean isProviderAccountIssue(Throwable ex) {
        if (ex == null) {
            return false;
        }
        Set<Throwable> visited = new HashSet<>();
        Throwable current = ex;
        while (current != null && visited.add(current)) {
            String message = current.getMessage();
            if (StringUtils.hasText(message) && containsAccountIssueMarker(message)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static String summarize(Throwable ex) {
        if (ex == null) {
            return "unknown";
        }
        Set<Throwable> visited = new HashSet<>();
        Throwable current = ex;
        while (current != null && visited.add(current)) {
            if (StringUtils.hasText(current.getMessage())) {
                return normalizeWhitespace(current.getMessage());
            }
            current = current.getCause();
        }
        return ex.getClass().getSimpleName();
    }

    private static boolean containsAccountIssueMarker(String message) {
        String normalized = normalizeWhitespace(message).toLowerCase(Locale.ROOT);
        for (String marker : ACCOUNT_ISSUE_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
