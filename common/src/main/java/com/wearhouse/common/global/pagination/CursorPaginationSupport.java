package com.wearhouse.common.global.pagination;

import java.util.List;
import java.util.function.Function;

public final class CursorPaginationSupport {

    private CursorPaginationSupport() {
    }

    public static <S, T> CursorPageResponse<T> toCursorPage(
            List<S> sources,
            int limit,
            Function<S, Long> cursorExtractor,
            Function<S, T> mapper
    ) {
        if (sources == null || sources.isEmpty() || limit <= 0) {
            return new CursorPageResponse<>(List.of(), null, false);
        }

        boolean hasNext = sources.size() > limit;
        int pageSize = Math.min(sources.size(), limit);
        List<S> pageSources = sources.subList(0, pageSize);
        List<T> items = pageSources.stream().map(mapper).toList();

        Long nextCursor = null;
        if (hasNext) {
            nextCursor = cursorExtractor.apply(pageSources.get(pageSources.size() - 1));
        }

        return new CursorPageResponse<>(items, nextCursor, hasNext);
    }
}
