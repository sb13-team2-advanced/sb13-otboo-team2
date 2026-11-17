package codeit.ootd.domain.auth.service.impl;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import codeit.ootd.domain.auth.entity.SocialAccount;
import codeit.ootd.domain.auth.exception.OAuthProviderAlreadyLinkedException;
import codeit.ootd.domain.auth.exception.SocialAccountAlreadyLinkedException;
import codeit.ootd.domain.auth.repository.SocialAccountRepository;
import codeit.ootd.domain.auth.service.SocialAccountService;
import codeit.ootd.domain.user.exception.UserNotFoundException;
import codeit.ootd.domain.user.repository.UserRepository;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialAccountServiceImpl implements SocialAccountService {

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findUserId(
            OAuthProvider provider,
            String providerUserId
    ) {
        return socialAccountRepository
                .findByProviderAndProviderUserId(
                        provider,
                        providerUserId
                )
                .map(SocialAccount::getUserId);
    }

    @Override
    @Transactional
    public void linkAccount(
            UUID userId,
            OAuthProvider provider,
            String providerUserId
    ) {
        Objects.requireNonNull(userId, "userId는 필수입니다.");
        Objects.requireNonNull(provider, "provider는 필수입니다.");
        Objects.requireNonNull(providerUserId, "providerUserId는 필수입니다.");

        if (providerUserId.isBlank()) {
            throw new IllegalArgumentException(
                    "소셜 사용자 ID는 비어 있을 수 없습니다."
            );
        }

        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException();
        }

        // 동일한 소셜 계정이 이미 연동되어 있는지 확인
        if (socialAccountRepository
                .findByProviderAndProviderUserId(provider, providerUserId)
                .isPresent()) {
            throw new SocialAccountAlreadyLinkedException();
        }

        // 동일 사용자의 동일 제공자 중복 연동 방지
        if (socialAccountRepository.existsByUserIdAndProvider(
                userId,
                provider
        )) {
            throw new OAuthProviderAlreadyLinkedException();
        }

        SocialAccount account = SocialAccount.create(
                userId,
                provider,
                providerUserId
        );

        socialAccountRepository.save(account);
    }
}
