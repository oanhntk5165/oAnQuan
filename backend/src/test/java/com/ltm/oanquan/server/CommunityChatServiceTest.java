package com.ltm.oanquan.server;

import com.ltm.oanquan.dao.CommunityChatDao;
import com.ltm.oanquan.dao.DaoException;
import com.ltm.oanquan.model.Users;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommunityChatServiceTest {
    private final CommunityChatDao dao = mock(CommunityChatDao.class);
    private final Users user = new Users(1, "mai", "", 0);

    @Test void rejectsAnonymousBlankAndOversizedWithoutSaving() {
        try (var service = new CommunityChatService(dao, message -> {})) {
            assertThrows(IllegalArgumentException.class, () -> service.send(null, "hello"));
            for (String text : List.of("", " \n\t", "\u00a0", "x".repeat(501)))
                assertThrows(IllegalArgumentException.class, () -> service.send(user, text));
            verifyNoInteractions(dao);
        }
    }

    @Test void persistsThenBroadcastsAndRateLimitIsPerAccount() throws Exception {
        AtomicLong clock = new AtomicLong(1000);
        var received = new LinkedBlockingQueue<Map<String, Object>>();
        var message = Map.<String, Object>of("id", "1", "content", "<script>alert(1)</script>");
        when(dao.insert(anyLong(), anyString(), anyString())).thenReturn(message);
        try (var service = new CommunityChatService(dao, received::add, clock::get)) {
            assertEquals(message, service.send(user, " <script>alert(1)</script> "));
            assertEquals(message, received.poll(2, TimeUnit.SECONDS));
            verify(dao).insert(1, "mai", "<script>alert(1)</script>");
            assertThrows(IllegalArgumentException.class, () -> service.send(new Users(1, "mai", "", 0), "again"));
            service.send(new Users(2, "minh", "", 0), "hello");
            clock.addAndGet(1000);
            service.send(user, "x".repeat(500));
            verify(dao, times(3)).insert(anyLong(), anyString(), anyString());
        }
    }

    @Test void databaseFailureDoesNotBroadcastOrConsumeRateLimit() {
        when(dao.insert(anyLong(), anyString(), anyString())).thenThrow(new DaoException("offline", null))
                .thenReturn(Map.of("id", "1"));
        var received = new LinkedBlockingQueue<Map<String, Object>>();
        try (var service = new CommunityChatService(dao, received::add)) {
            assertThrows(DaoException.class, () -> service.send(user, "hello"));
            assertTrue(received.isEmpty());
            assertDoesNotThrow(() -> service.send(user, "retry"));
        }
    }

    @Test void cursorPageReturnsFiftyOldestToNewestAndHasMore() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int id = 100; id >= 50; id--) rows.add(Map.of("id", Integer.toString(id)));
        when(dao.history(101, 51)).thenReturn(rows);
        when(dao.history(51, 51)).thenReturn(List.of(Map.of("id", "50")));
        try (var service = new CommunityChatService(dao, message -> {})) {
            var page = service.history(101);
            var messages = (List<?>) page.get("messages");
            assertEquals(50, messages.size());
            assertEquals(Map.of("id", "51"), messages.get(0));
            assertEquals(Map.of("id", "100"), messages.get(49));
            assertEquals(true, page.get("hasMore"));
            assertEquals(false, service.history(51).get("hasMore"));
            assertThrows(IllegalArgumentException.class, () -> service.history(0));
        }
    }
}
