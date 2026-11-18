package codeit.ootd.domain.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import codeit.ootd.domain.auth.entity.OAuthProvider;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class OAuth2UserInfoExtractorTest {

    private final OAuth2UserInfoExtractor extractor =
            new OAuth2UserInfoExtractor();

    @Test
    @DisplayName("Google 사용자 정보에서 ID와 이메일 및 이름을 추출한다")
    void extract_google_success() {
        // given
        Map<String, Object> attributes = Map.of(
                "sub", "google-123",
                "email", "google@example.com",
                "name", "구글 사용자"
        );

        // when
        OAuth2UserInfo result = extractor.extract(
                OAuthProvider.GOOGLE,
                attributes
        );

        // then
        assertThat(result.provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(result.providerUserId()).isEqualTo("google-123");
        assertThat(result.email()).isEqualTo("google@example.com");
        assertThat(result.name()).isEqualTo("구글 사용자");
    }

    @Test
    @DisplayName("Kakao 사용자 정보에서 ID와 이메일 및 닉네임을 추출한다")
    void extract_kakao_success() {
        // given
        Map<String, Object> attributes = Map.of(
                "id", 123456789L,
                "kakao_account", Map.of(
                        "email", "kakao@example.com",
                        "profile", Map.of(
                                "nickname", "카카오 사용자"
                        )
                )
        );

        // when
        OAuth2UserInfo result = extractor.extract(
                OAuthProvider.KAKAO,
                attributes
        );

        // then
        assertThat(result.provider()).isEqualTo(OAuthProvider.KAKAO);
        assertThat(result.providerUserId()).isEqualTo("123456789");
        assertThat(result.email()).isEqualTo("kakao@example.com");
        assertThat(result.name()).isEqualTo("카카오 사용자");
    }

    @Test
    @DisplayName("Kakao 이메일이 제공되지 않아도 사용자 ID와 닉네임을 추출한다")
    void extract_kakao_without_email() {
        // given
        Map<String, Object> attributes = Map.of(
                "id", 123456789L,
                "kakao_account", Map.of(
                        "profile", Map.of(
                                "nickname", "카카오 사용자"
                        )
                )
        );

        // when
        OAuth2UserInfo result = extractor.extract(
                OAuthProvider.KAKAO,
                attributes
        );

        // then
        assertThat(result.providerUserId()).isEqualTo("123456789");
        assertThat(result.email()).isNull();
        assertThat(result.name()).isEqualTo("카카오 사용자");
    }


    @ParameterizedTest
    @EnumSource(value = OAuthProvider.class, names = {"GOOGLE", "KAKAO"})
    @DisplayName("필수 소셜 사용자 ID가 없으면 예외를 발생시킨다")
    void extract_missingProviderUserId_throwsException(OAuthProvider provider) {
        // given
        Map<String, Object> attributes = Map.of();

        // when & then
        assertThatThrownBy(() -> extractor.extract(provider, attributes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("소셜 사용자 ID가 없습니다.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("소셜 사용자 ID가 빈 문자열 또는 공백이면 예외를 발생시킨다")
    void extract_blankProviderUserId_throwsException(String providerUserId) {
        // given
        Map<String, Object> attributes = Map.of(
                "sub", providerUserId
        );

        // when & then
        assertThatThrownBy(() ->
                extractor.extract(OAuthProvider.GOOGLE, attributes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("소셜 사용자 ID가 없습니다.");
    }

    @Test
    @DisplayName("Kakao 사용자 정보의 중첩 구조가 잘못되면 선택 정보를 무시한다")
    void extract_kakao_invalidNestedAttributes_success() {
        // given
        Map<String, Object> attributes = Map.of(
                "id", 123456789L,
                "kakao_account", Map.of(
                        123, "ignored",
                        "email", "   ",
                        "profile", "invalid-profile"
                )
        );

        // when
        OAuth2UserInfo result = extractor.extract(
                OAuthProvider.KAKAO,
                attributes
        );

        // then
        assertThat(result.providerUserId()).isEqualTo("123456789");
        assertThat(result.email()).isNull();
        assertThat(result.name()).isNull();
    }


}
