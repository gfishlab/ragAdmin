package com.ragadmin.server.auth.model;

import java.util.Arrays;
import java.util.List;

public enum AdminPermissionCode {

    DASHBOARD_VIEW,
    KB_MANAGE,
    MODEL_MANAGE,
    TASK_VIEW,
    TASK_OPERATE,
    AUDIT_VIEW,
    STATISTICS_VIEW,
    CHAT_FEEDBACK_VIEW,
    PROMPT_TEMPLATE_MANAGE,
    USER_MANAGE;

    public static List<String> allCodes() {
        return Arrays.stream(values())
                .map(AdminPermissionCode::name)
                .toList();
    }
}
