package codeit.ootd.domain.auth.service.impl;

import codeit.ootd.domain.auth.service.TemporaryPasswordMailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpTemporaryPasswordMailSender
        implements TemporaryPasswordMailSender {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Override
    public void send(String email, String temporaryPassword) {
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(email);
        message.setSubject("[OTBOO] 임시 비밀번호 안내");
        message.setText("""
                안녕하세요. OTBOO입니다.

                요청하신 임시 비밀번호를 안내드립니다.

                임시 비밀번호: %s

                유효기간은 발급 시점부터 3분입니다.
                로그인 후 비밀번호를 변경해 주세요.

                본인이 요청하지 않았다면 이 메일을 무시해 주세요.
                """.formatted(temporaryPassword));

        mailSender.send(message);
    }
}
