package com.wearhouse.common.global.pagination;

import java.util.List;

public record CursorPageResponse<T>(
        List<T> items,
        Long nextCursor,
        boolean hasNext
) {
}
