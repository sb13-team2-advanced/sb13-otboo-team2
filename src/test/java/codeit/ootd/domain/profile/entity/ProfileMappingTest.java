package codeit.ootd.domain.profile.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.domain.profile.repository.ProfileRepository;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필 엔티티의 저장·조회 매핑과 사용자당 프로필 1개 제약을 검증한다.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ProfileMappingTest {

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 프로필은_선택_정보_없이_저장되고_시간이_기록된다() {
        // given
        UUID userId = UUID.randomUUID();
        UUID id = profileRepository.saveAndFlush(new Profile(userId)).getId();
        entityManager.clear();

        // when
        Profile found = profileRepository.findByUserId(userId).orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getGender()).isNull();
        assertThat(found.getBirthDate()).isNull();
        assertThat(found.getTemperatureSensitivity()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void 같은_사용자의_프로필은_하나만_저장할_수_있다() {
        // given
        UUID userId = UUID.randomUUID();
        profileRepository.saveAndFlush(new Profile(userId));

        // when/then
        assertThatThrownBy(() -> profileRepository.saveAndFlush(new Profile(userId)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
