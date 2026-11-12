package codeit.ootd.global.security;

import codeit.ootd.domain.auth.event.AuthSessionRevokedEvent;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Slf4j
@Component
public class AuthSessionWebSocketRegistry {

    private static final CloseStatus SESSION_REVOKED =
            CloseStatus.POLICY_VIOLATION.withReason("Authentication session revoked");

    // shortcut: 이 서버의 연결만 관리한다. 여러 서버 배포 시 폐기 이벤트를 서버 간에도 전달해야 한다.
    private final ConcurrentMap<String, Connection> connections = new ConcurrentHashMap<>();

    public void add(WebSocketSession session) {
        connections.put(session.getId(), new Connection(session, null));
    }

    public void bind(String webSocketSessionId, UUID authSessionId) {
        Connection connection = connections.computeIfPresent(webSocketSessionId,
                (id, current) -> new Connection(current.session(), authSessionId));
        if (connection == null || !connection.session().isOpen()) {
            throw new BadCredentialsException("열려 있는 WebSocket 연결이 없습니다.");
        }
    }

    public void remove(String webSocketSessionId) {
        connections.remove(webSocketSessionId);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessionRevoked(AuthSessionRevokedEvent event) {
        for (Connection connection : connections.values()) {
            if (!event.sessionId().equals(connection.authSessionId())) {
                continue;
            }
            WebSocketSession session = connection.session();
            try {
                if (session.isOpen()) {
                    session.close(SESSION_REVOKED);
                }
                connections.remove(session.getId(), connection);
            } catch (IOException | RuntimeException e) {
                // 한 연결의 종료 실패가 다른 탭의 종료를 막지 않게 한다.
                log.warn("인증 세션 폐기에 따른 WebSocket 종료 실패: sessionId={}", session.getId(), e);
            }
        }
    }

    private record Connection(WebSocketSession session, UUID authSessionId) {
    }
}
