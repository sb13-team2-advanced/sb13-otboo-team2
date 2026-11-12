package codeit.ootd.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.auth.dto.response.SignInResult;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.repository.AuthSessionRepository;
import codeit.ootd.domain.auth.repository.RefreshTokenRepository;
import codeit.ootd.domain.auth.service.AuthService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.user.service.UserService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthSessionWebSocketIntegrationTest {

    @LocalServerPort private int port;
    @Autowired private AuthService authService;
    @Autowired private UserService userService;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private AuthSessionRepository authSessions;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private PlatformTransactionManager transactionManager;

    private final List<UUID> sessionIds = new ArrayList<>();
    private final List<StompSession> connections = new ArrayList<>();
    private WebSocketStompClient client;
    private User user;
    private SignInResult login;

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(User.create("웹소켓 사용자", UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("password123!")));
        login = signIn();
        client = new WebSocketStompClient(new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))));
    }

    @AfterEach
    void tearDown() {
        for (StompSession connection : connections) {
            if (connection.isConnected()) {
                connection.disconnect();
            }
        }
        if (client != null) {
            client.stop();
        }
        for (UUID sessionId : sessionIds) {
            refreshTokens.findBySessionId(sessionId).ifPresent(refreshTokens::delete);
        }
        authSessions.deleteAllById(sessionIds);
        users.deleteById(user.getId());
    }

    @ParameterizedTest
    @EnumSource(Revocation.class)
    @DisplayName("로그아웃·계정 잠금·재로그인은 커밋 후 기존 세션의 모든 탭을 종료한다")
    void closesConnectionsAfterCommit(Revocation revocation) throws Exception {
        Connection firstTab = connect(login.accessToken());
        Connection secondTab = connect(login.accessToken());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            switch (revocation) {
                case LOGOUT -> authService.signOut(login.refreshToken());
                case LOCK -> userService.updateLock(user.getId(), true);
                case SIGN_IN -> login = signIn();
            }
            assertThat(firstTab.closed()).isNotDone();
            assertThat(secondTab.closed()).isNotDone();
        });

        firstTab.closed().get(5, TimeUnit.SECONDS);
        secondTab.closed().get(5, TimeUnit.SECONDS);
        assertThat(firstTab.session().isConnected()).isFalse();
        assertThat(secondTab.session().isConnected()).isFalse();
        if (revocation == Revocation.SIGN_IN) {
            assertThat(connect(login.accessToken()).session().isConnected()).isTrue();
        }
    }

    @Test
    @DisplayName("로그아웃이 롤백되면 기존 WebSocket 연결을 유지한다")
    void keepsConnectionOnRollback() throws Exception {
        Connection connection = connect(login.accessToken());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            authService.signOut(login.refreshToken());
            status.setRollbackOnly();
        });

        assertThat(connection.closed()).isNotDone();
        assertThat(connection.session().isConnected()).isTrue();
        assertThat(authSessions.findById(sessionIds.get(0)).orElseThrow().getRevokedAt()).isNull();
    }

    private SignInResult signIn() {
        SignInResult result = authService.signIn(user.getEmail(), "password123!");
        sessionIds.add(UUID.fromString(jwtProvider.parseAccessToken(result.accessToken())
                .get("sessionId", String.class)));
        return result;
    }

    private Connection connect(String accessToken) throws Exception {
        CompletableFuture<Throwable> closed = new CompletableFuture<>();
        StompHeaders headers = new StompHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        StompSession session = client.connectAsync("http://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                headers, new StompSessionHandlerAdapter() {
                    @Override
                    public void handleTransportError(StompSession session, Throwable exception) {
                        closed.complete(exception);
                    }
                }).get(5, TimeUnit.SECONDS);
        connections.add(session);
        return new Connection(session, closed);
    }

    private enum Revocation { LOGOUT, LOCK, SIGN_IN }

    private record Connection(StompSession session, CompletableFuture<Throwable> closed) {
    }
}
