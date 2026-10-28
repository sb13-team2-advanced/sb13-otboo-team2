package codeit.ootd.domain.auth.jwt;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.time.Duration;

@Slf4j
@Configuration
public class JwtConfig {

    @Bean
    public JwtProvider jwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") Duration expiration
    ) {
        SecretKey secretKey = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(secret)
        );

        JwtProvider provider = new JwtProvider(secretKey, expiration);

        log.info("JWT Provider 초기화 완료");

        return provider;
    }
}