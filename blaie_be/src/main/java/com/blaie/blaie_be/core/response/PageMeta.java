package com.blaie.blaie_be.core.response;

public record PageMeta(
        String nextCursor,
        boolean hasMore,
        int limit
) {
    public static PageMeta of(String nextCursor, boolean hasMore, int limit) {
        return new PageMeta(nextCursor, hasMore, limit);
    }
}
