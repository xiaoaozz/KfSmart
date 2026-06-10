package com.smart.kf.utils.pagination;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public record PageQuery(int page, int size, String cursor) {
    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 10;
    public static final int MAX_SIZE = 100;
    public static final int DEEP_PAGE_OFFSET_LIMIT = 10_000;

    public static PageQuery of(Integer page, Integer size, String cursor) {
        int normalizedPage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int normalizedSize = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return new PageQuery(normalizedPage, normalizedSize, cursor);
    }

    public int offset() {
        return (page - 1) * size;
    }

    public int startOffset() {
        if (cursor == null || cursor.isBlank()) {
            ensureNotDeepOffset();
            return offset();
        }
        return decodeCursor(cursor);
    }

    public void ensureNotDeepOffset() {
        if (offset() > DEEP_PAGE_OFFSET_LIMIT) {
            throw new IllegalArgumentException("当前页码过深，请使用 cursor/nextCursor 顺序翻页");
        }
    }

    public static String encodeCursor(int nextOffset) {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(("offset:" + nextOffset).getBytes(StandardCharsets.UTF_8));
    }

    private static int decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!decoded.startsWith("offset:")) {
                throw new IllegalArgumentException("cursor 格式不正确");
            }
            int offset = Integer.parseInt(decoded.substring("offset:".length()));
            return Math.max(0, offset);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("cursor 无效，请重新从第一页查询");
        }
    }
}
