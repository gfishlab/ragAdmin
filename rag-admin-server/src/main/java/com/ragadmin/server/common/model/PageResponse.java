package com.ragadmin.server.common.model;

import java.util.List;

public record PageResponse<T>(List<T> list, long pageNo, long pageSize, long total) {
}
