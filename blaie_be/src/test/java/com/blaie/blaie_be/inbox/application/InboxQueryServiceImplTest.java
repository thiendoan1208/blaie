package com.blaie.blaie_be.inbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blaie.blaie_be.authz.application.AuthorizationService;
import com.blaie.blaie_be.authz.domain.PermissionAction;
import com.blaie.blaie_be.core.cursor.CursorProperties;
import com.blaie.blaie_be.core.cursor.SignedCursorCodec;
import com.blaie.blaie_be.core.error.AppException;
import com.blaie.blaie_be.core.error.ErrorCode;
import com.blaie.blaie_be.core.security.CurrentUser;
import com.blaie.blaie_be.core.security.CurrentUserHolder;
import com.blaie.blaie_be.inbox.application.port.InboxItemQueryPort;
import com.blaie.blaie_be.inbox.application.result.InboxItemResult;
import com.blaie.blaie_be.inbox.application.result.InboxPageResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InboxQueryServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    private final InboxItemQueryPort itemQuery = mock(InboxItemQueryPort.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final InboxQueryService service =
            new InboxQueryServiceImpl(itemQuery, cursorCodec(), authorization);

    @Test
    void paginatesOwnedItemsWithAUserBoundSignedCursor() {
        UUID userId = UUID.randomUUID();
        InboxItemResult newest = item("Newest", NOW);
        InboxItemResult oldestOnPage = item("Older", NOW.minusSeconds(1));
        InboxItemResult lookahead = item("Lookahead", NOW.minusSeconds(2));
        when(itemQuery.findFirstPage(userId, 3))
                .thenReturn(List.of(newest, oldestOnPage, lookahead));

        InboxPageResult firstPage = runAs(userId, () -> service.inbox(null, 2));

        assertThat(firstPage.items()).containsExactly(newest, oldestOnPage);
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).startsWith("v1.");
        verify(authorization).require(PermissionAction.INBOX_READ);

        when(itemQuery.findPageAfter(
                userId,
                oldestOnPage.createdAt(),
                oldestOnPage.id(),
                3
        )).thenReturn(List.of(lookahead));

        InboxPageResult secondPage =
                runAs(userId, () -> service.inbox(firstPage.nextCursor(), 2));

        assertThat(secondPage.items()).containsExactly(lookahead);
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void rejectsAValidCursorWhenAnotherUserPresentsIt() {
        UUID ownerId = UUID.randomUUID();
        InboxItemResult item = item("Private", NOW);
        when(itemQuery.findFirstPage(ownerId, 2)).thenReturn(List.of(item, item("More", NOW.minusSeconds(1))));
        String cursor = runAs(ownerId, () -> service.inbox(null, 1)).nextCursor();

        assertThatThrownBy(() -> runAs(UUID.randomUUID(), () -> service.inbox(cursor, 1)))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void readsOneOwnedItemAndUsesTheStableNotFoundErrorOtherwise() {
        UUID userId = UUID.randomUUID();
        InboxItemResult item = item("Owned", NOW);
        when(itemQuery.findOwned(item.id(), userId)).thenReturn(Optional.of(item));

        assertThat(runAs(userId, () -> service.inboxItem(item.id()))).isEqualTo(item);
        verify(authorization).require(PermissionAction.ITEM_READ);

        UUID missingId = UUID.randomUUID();
        when(itemQuery.findOwned(missingId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> runAs(userId, () -> service.inboxItem(missingId)))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CAPTURE_ITEM_NOT_FOUND));
    }

    private InboxItemResult item(String text, Instant createdAt) {
        return new InboxItemResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                text,
                "task",
                "completed",
                createdAt
        );
    }

    private SignedCursorCodec cursorCodec() {
        CursorProperties properties = new CursorProperties();
        properties.setActiveKeyId("v1");
        properties.setActiveSecret("inbox-query-test-cursor-secret-1234567890");
        return new SignedCursorCodec(properties);
    }

    private <T> T runAs(UUID userId, java.util.function.Supplier<T> supplier) {
        return CurrentUserHolder.runAs(new CurrentUser(userId.toString(), false, Set.of()), supplier);
    }
}
