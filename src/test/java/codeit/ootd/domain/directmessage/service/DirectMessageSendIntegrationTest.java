package codeit.ootd.domain.directmessage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.directmessage.controller.DirectMessageStompController;
import codeit.ootd.domain.directmessage.dto.DirectMessageDto;
import codeit.ootd.domain.directmessage.dto.request.DirectMessageSendRequest;
import codeit.ootd.domain.directmessage.event.DirectMessageEventListener;
import codeit.ootd.domain.directmessage.repository.DirectMessageRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.global.exception.OtbooException;
import codeit.ootd.global.config.JpaAuditingConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import({DirectMessageService.class, DirectMessageStompController.class,
        DirectMessageEventListener.class, JpaAuditingConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ActiveProfiles("test")
class DirectMessageSendIntegrationTest {

    @Autowired private DirectMessageService service;
    @Autowired private DirectMessageStompController controller;
    @Autowired private DirectMessageRepository messages;
    @Autowired private UserRepository users;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private SimpMessagingTemplate messagingTemplate;

    private User sender;
    private User receiver;

    @BeforeEach
    void setUp() {
        sender = users.saveAndFlush(User.create("sender", UUID.randomUUID() + "@example.com", "encoded"));
        receiver = users.saveAndFlush(User.create("receiver", UUID.randomUUID() + "@example.com", "encoded"));
    }

    @AfterEach
    void tearDown() {
        messages.deleteAll(messages.findAll().stream()
                .filter(message -> message.getSenderId().equals(sender.getId())
                        || message.getSenderId().equals(receiver.getId())).toList());
        users.deleteAllById(List.of(sender.getId(), receiver.getId()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sendsSavedDtoOnlyAfterCommit(boolean reverse) {
        User from = reverse ? receiver : sender;
        User to = reverse ? sender : receiver;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            controller.send(request(from, to, "hello"), from.getId());
            assertThat(messages.count()).isPositive();
            verifyNoInteractions(messagingTemplate);
        });

        var payload = ArgumentCaptor.forClass(DirectMessageDto.class);
        verify(messagingTemplate).convertAndSend(eq(destination()), payload.capture());
        DirectMessageDto dto = payload.getValue();
        assertThat(dto.id()).isNotNull();
        assertThat(dto.createdAt()).isNotNull();
        assertThat(dto.sender().userId()).isEqualTo(from.getId());
        assertThat(dto.sender().name()).isEqualTo(from.getName());
        assertThat(dto.receiver().userId()).isEqualTo(to.getId());
        assertThat(dto.receiver().name()).isEqualTo(to.getName());
        assertThat(dto.content()).isEqualTo("hello");
        var saved = messages.findById(dto.id()).orElseThrow();
        assertThat(saved.getContent()).isEqualTo(dto.content());
        assertThat(saved.getSenderId()).isEqualTo(from.getId());
        assertThat(saved.getReceiverId()).isEqualTo(to.getId());
    }

    @Test
    void rollbackDoesNotPersistOrBroadcast() {
        DirectMessageDto dto = new TransactionTemplate(transactionManager).execute(status -> {
            DirectMessageDto result = service.send(request(sender, receiver, "rollback"), sender.getId());
            status.setRollbackOnly();
            return result;
        });
        assertThat(messages.findById(dto.id())).isEmpty();
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void rejectsForgedSender() {
        assertRejected(request(receiver, sender, "forged"), sender.getId());
    }

    @Test
    void rejectsMissingReceiver() {
        assertRejected(new DirectMessageSendRequest(sender.getId(), UUID.randomUUID(), "hello"), sender.getId());
    }

    @Test
    void rejectsBlankContent() {
        assertRejected(request(sender, receiver, " \n\t"), sender.getId());
    }

    private void assertRejected(DirectMessageSendRequest request, UUID userId) {
        long count = messages.count();
        assertThatThrownBy(() -> service.send(request, userId)).isInstanceOf(OtbooException.class);
        assertThat(messages.count()).isEqualTo(count);
        verifyNoInteractions(messagingTemplate);
    }

    private DirectMessageSendRequest request(User from, User to, String content) {
        return new DirectMessageSendRequest(from.getId(), to.getId(), content);
    }

    private String destination() {
        var ids = List.of(sender.getId().toString(), receiver.getId().toString())
                .stream().sorted().toList();
        return "/sub/direct-messages_" + String.join("_", ids);
    }
}
