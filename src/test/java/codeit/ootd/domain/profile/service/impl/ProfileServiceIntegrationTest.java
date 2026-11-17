package codeit.ootd.domain.profile.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.profile.dto.request.ProfileLocationRequest;
import codeit.ootd.domain.profile.dto.request.ProfileUpdateRequest;
import codeit.ootd.domain.profile.dto.response.ProfileDto;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.enums.Gender;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.profile.service.ProfileService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.weather.dto.response.WeatherAPILocation;
import codeit.ootd.domain.weather.service.WeatherLocationService;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로필이 없는 사용자의 첫 수정, 반복 수정, 이름 반영이 실제 저장소에서 의도대로 동작하는지 검증한다.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ProfileServiceIntegrationTest {

    @Autowired
    private ProfileService profileService;
    @Autowired
    private ProfileRepository profileRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;
    // 카카오 API를 실제로 부르지 않도록 대체한다.
    @MockitoBean
    private WeatherLocationService weatherLocationService;

    @Test
    void 프로필이_없는_사용자를_조회하면_이름만_담긴_빈_프로필이_내려오고_저장되지_않는다() {
        UUID userId = newUser("완이");

        ProfileDto dto = profileService.getProfile(userId);
        entityManager.flush();

        assertThat(dto.name()).isEqualTo("완이");
        assertThat(dto.gender()).isNull();
        assertThat(profileRepository.findByUserId(userId)).isEmpty();
    }

    @Test
    void 프로필이_없는_사용자를_수정하면_프로필이_만들어지고_이름도_사용자에_반영된다() {
        UUID userId = newUser("완이");

        profileService.updateProfile(userId, userId,
                new ProfileUpdateRequest("새 이름", Gender.FEMALE, LocalDate.of(2000, 1, 2), null, 4), null);
        entityManager.flush();
        entityManager.clear();

        Profile saved = profileRepository.findByUserId(userId).orElseThrow();
        assertThat(saved.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 2));
        assertThat(saved.getTemperatureSensitivity()).isEqualTo(4);
        assertThat(userRepository.findById(userId).orElseThrow().getName()).isEqualTo("새 이름");

        ProfileDto found = profileService.getProfile(userId);
        assertThat(found.name()).isEqualTo("새 이름");
        assertThat(found.gender()).isEqualTo(Gender.FEMALE);
    }

    @Test
    void 여러_번_수정해도_프로필은_하나만_유지되고_보내지_않은_값은_남는다() {
        UUID userId = newUser("완이");
        profileService.updateProfile(userId, userId,
                new ProfileUpdateRequest(null, Gender.MALE, LocalDate.of(1999, 5, 6), null, 2), null);
        entityManager.flush();
        entityManager.clear();

        profileService.updateProfile(userId, userId, new ProfileUpdateRequest(null, null, null, null, 5), null);
        entityManager.flush();
        entityManager.clear();

        Profile saved = profileRepository.findByUserId(userId).orElseThrow();
        assertThat(saved.getGender()).isEqualTo(Gender.MALE);
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(1999, 5, 6));
        assertThat(saved.getTemperatureSensitivity()).isEqualTo(5);
        assertThat(profileRepository.count()).isEqualTo(1L);
    }

    @Test
    void 위치를_저장하면_위경도만_DB에_남고_조회_때_지명이_채워진다() {
        UUID userId = newUser("완이");
        org.mockito.BDDMockito.given(weatherLocationService.getLocation(37.5, 127.0))
                .willReturn(new WeatherAPILocation(37.5, 127.0, 37.5, 127.0, List.of("서울특별시", "강남구", "역삼동")));

        profileService.updateProfile(userId, userId,
                new ProfileUpdateRequest(null, null, null, new ProfileLocationRequest(37.5, 127.0), null), null);
        entityManager.flush();
        entityManager.clear();

        Profile saved = profileRepository.findByUserId(userId).orElseThrow();
        assertThat(saved.getLatitude()).isEqualTo(37.5);
        assertThat(saved.getLongitude()).isEqualTo(127.0);
        assertThat(saved.getLocationNames()).isNull();
        assertThat(profileService.getProfile(userId).location().locationNames())
                .containsExactly("서울특별시", "강남구", "역삼동");
    }

    private UUID newUser(String name) {
        return userRepository.saveAndFlush(User.create(name, UUID.randomUUID() + "@test.com", "encoded")).getId();
    }
}
