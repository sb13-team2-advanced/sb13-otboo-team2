package codeit.ootd.global.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import codeit.ootd.domain.auth.event.AuthSessionRevokedEvent;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class AuthSessionWebSocketRegistryTest {

    private final AuthSessionWebSocketRegistry registry = new AuthSessionWebSocketRegistry();

    @Test
    @DisplayName("한 탭의 종료가 실패해도 다른 탭을 종료하며 다른 인증 세션은 유지한다")
    void closesOnlyRevokedSessionDespiteCloseFailure() throws Exception {
        UUID revoked = UUID.randomUUID();
        WebSocketSession broken = register("broken", revoked);
        WebSocketSession tab = register("tab", revoked);
        WebSocketSession anotherSession = register("another", UUID.randomUUID());
        doThrow(new IOException("close failed")).when(broken).close(any(CloseStatus.class));

        registry.onSessionRevoked(new AuthSessionRevokedEvent(revoked));

        verify(tab).close(CloseStatus.POLICY_VIOLATION.withReason("Authentication session revoked"));
        verify(anotherSession, never()).close(any(CloseStatus.class));
    }

    private WebSocketSession register(String id, UUID authSessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.isOpen()).thenReturn(true);
        registry.add(session);
        registry.bind(id, authSessionId);
        return session;
    }
}
