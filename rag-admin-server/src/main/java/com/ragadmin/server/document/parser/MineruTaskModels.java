package com.ragadmin.server.document.parser;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

final class MineruTaskModels {

    private MineruTaskModels() {
    }

    record CreateTaskRequest(
            String url,
            @JsonProperty("model_version") String modelVersion,
            @JsonProperty("file_name") String fileName
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CreateTaskResponse(int code, String msg, CreateTaskData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CreateTaskData(@JsonProperty("task_id") String taskId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TaskResultResponse(int code, String msg, TaskResultData data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TaskResultData(
            @JsonProperty("task_id") String taskId,
            String state,
            @JsonProperty("full_zip_url") String fullZipUrl,
            @JsonProperty("full_md_url") String fullMdUrl,
            @JsonProperty("md_url") String mdUrl,
            @JsonProperty("err_msg") String errMsg
    ) {
    }

    record MarkdownResult(String markdown, String source) {
    }
}
