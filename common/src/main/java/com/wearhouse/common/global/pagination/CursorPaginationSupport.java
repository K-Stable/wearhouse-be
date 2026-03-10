package com.wearhouse.common.global.pagination;

import java.util.List;
import java.util.function.Function;

public final class CursorPaginationSupport {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    private CursorPaginationSupport() {
    }

    public static int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }

    public static <S, T> CursorPageResponse<T> toCursorPage(
            List<S> sources,
            int limit,
            Function<S, Long> cursorExtractor,
            Function<S, T> mapper
    ) {
        boolean hasNext = sources.size() > limit;
        List<S> pageSources = hasNext ? sources.subList(0, limit) : sources;
        List<T> items = pageSources.stream().map(mapper).toList();
        Long nextCursor = hasNext && !pageSources.isEmpty()
                ? cursorExtractor.apply(pageSources.get(pageSources.size() - 1))
                : null;
        return new CursorPageResponse<>(items, nextCursor, hasNext);
    }
}
