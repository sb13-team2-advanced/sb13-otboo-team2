package codeit.ootd.domain.auth.service.impl;

import codeit.ootd.domain.auth.service.TemporaryPasswordGenerator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureTemporaryPasswordGenerator
        implements TemporaryPasswordGenerator {

    private static final String CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private static final int PASSWORD_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        StringBuilder password = new StringBuilder(PASSWORD_LENGTH);

        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            int index = secureRandom.nextInt(CHARACTERS.length());
            password.append(CHARACTERS.charAt(index));
        }

        return password.toString();
    }
}
