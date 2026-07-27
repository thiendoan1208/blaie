package com.blaie.blaie_be.inbox.application;

import com.blaie.blaie_be.authz.application.AuthorizationService;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.core.cursor.SignedCursorCodec;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import com.blaie.blaie_be.inbox.application.port.InboxItemQueryPort;
import com.blaie.blaie_be.inbox.application.result.InboxItemResult;
import com.blaie.blaie_be.inbox.application.result.InboxPageResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InboxQueryServiceImpl implements InboxQueryService {
    private static final int MAX_LIMIT = 50;
    private static final String CURSOR_AUDIENCE = "inbox-items";

    private final InboxItemQueryPort itemQuery;
    private final SignedCursorCodec cursorCodec;
    private final AuthorizationService authorization;

    public InboxQueryServiceImpl(
            InboxItemQueryPort itemQuery,
            SignedCursorCodec cursorCodec,
            AuthorizationService authorization
    ) {
        this.itemQuery = itemQuery;
        this.cursorCodec = cursorCodec;
        this.authorization = authorization;
    }

    @Override
    public InboxPageResult inbox(String cursor, int limit) {
        authorization.require(PermissionAction.INBOX_READ);
        int safeLimit = validateLimit(limit);
        UUID userId = currentUserId();
        Cursor decodedCursor = cursor == null || cursor.isBlank() ? null : decodeCursor(cursor, userId);
        List<InboxItemResult> records = decodedCursor == null
                ? itemQuery.findFirstPage(userId, safeLimit + 1)
                : itemQuery.findPageAfter(
                        userId,
                        decodedCursor.createdAt(),
                        decodedCursor.itemId(),
                        safeLimit + 1
                );

        boolean hasMore = records.size() > safeLimit;
        List<InboxItemResult> items = hasMore ? records.subList(0, safeLimit) : records;
        String nextCursor = hasMore ? encodeCursor(items.getLast(), userId) : null;
        return new InboxPageResult(items, nextCursor, hasMore, safeLimit);
    }

    @Override
    public InboxItemResult inboxItem(UUID itemId) {
        authorization.require(PermissionAction.ITEM_READ);
        return itemQuery.findOwned(itemId, currentUserId())
                .orElseThrow(() -> new AppException(ErrorCode.CAPTURE_ITEM_NOT_FOUND));
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(CurrentUserHolder.requireCurrentUser().userId());
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private int validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "limit must be between 1 and " + MAX_LIMIT);
        }
        return limit;
    }

    private String encodeCursor(InboxItemResult item, UUID userId) {
        return cursorCodec.encode(
                CURSOR_AUDIENCE,
                userId + "|" + item.createdAt() + "|" + item.id()
        );
    }

    private Cursor decodeCursor(String cursor, UUID userId) {
        try {
            String decoded = cursorCodec.decode(CURSOR_AUDIENCE, cursor);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 3 || !userId.equals(UUID.fromString(parts[0]))) {
                throw new IllegalArgumentException();
            }
            return new Cursor(Instant.parse(parts[1]), UUID.fromString(parts[2]));
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "cursor is invalid");
        }
    }

    private record Cursor(Instant createdAt, UUID itemId) {
    }
}
