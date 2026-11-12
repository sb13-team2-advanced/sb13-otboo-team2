package codeit.ootd.global.security;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

public class AuthSessionWebSocketHandlerDecorator extends WebSocketHandlerDecorator {

    private final AuthSessionWebSocketRegistry sessions;

    public AuthSessionWebSocketHandlerDecorator(
            WebSocketHandler delegate, AuthSessionWebSocketRegistry sessions) {
        super(delegate);
        this.sessions = sessions;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        try {
            super.afterConnectionEstablished(session);
        } catch (Exception e) {
            sessions.remove(session.getId());
            throw e;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        try {
            super.afterConnectionClosed(session, closeStatus);
        } finally {
            sessions.remove(session.getId());
        }
    }
}
