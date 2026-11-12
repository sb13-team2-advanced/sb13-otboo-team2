package codeit.ootd.domain.auth.service;

import codeit.ootd.domain.auth.service.impl.SmtpTemporaryPasswordMailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.mockito.ArgumentCaptor;

class SmtpTemporaryPasswordMailSenderTest {

    @Test
    @DisplayName("임시 비밀번호 이메일에 수신자와 3분 유효기간을 포함한다")
    void send_success() {
        // given
        JavaMailSender javaMailSender = mock(JavaMailSender.class);

        SmtpTemporaryPasswordMailSender mailSender =
                new SmtpTemporaryPasswordMailSender(javaMailSender);

        ReflectionTestUtils.setField(
                mailSender,
                "senderEmail",
                "noreply@example.com"
        );

        String email = "test@example.com";
        String temporaryPassword = "temporary123!";

        // when
        mailSender.send(email, temporaryPassword);

        // then
        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(javaMailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();

        assertThat(message.getFrom()).isEqualTo("noreply@example.com");
        assertThat(message.getTo()).containsExactly(email);
        assertThat(message.getSubject()).isEqualTo("[OTBOO] 임시 비밀번호 안내");
        assertThat(message.getText())
                .contains(temporaryPassword)
                .contains("3분");
    }
}
