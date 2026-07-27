package com.blaie.blaie_be.inbox.application;

import com.blaie.blaie_be.inbox.application.result.InboxItemResult;
import com.blaie.blaie_be.inbox.application.result.InboxPageResult;
import java.util.UUID;

public interface InboxQueryService {
    InboxPageResult inbox(String cursor, int limit);

    InboxItemResult inboxItem(UUID itemId);
}
