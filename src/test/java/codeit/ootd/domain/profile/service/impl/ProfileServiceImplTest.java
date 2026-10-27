package codeit.ootd.domain.profile.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 기본 프로필 생성이 없을 때만 저장하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    @Mock
    private ProfileRepository profileRepository;

    @InjectMocks
    private ProfileServiceImpl profileService;

    @Test
    void 프로필이_없으면_기본_프로필을_저장한다() {
        // given
        UUID userId = UUID.randomUUID();
        given(profileRepository.findByUserId(userId)).willReturn(Optional.empty());

        // when
        profileService.createDefaultProfile(userId);

        // then
        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        then(profileRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
    }

    @Test
    void 이미_프로필이_있으면_새로_저장하지_않는다() {
        // given
        UUID userId = UUID.randomUUID();
        given(profileRepository.findByUserId(userId)).willReturn(Optional.of(new Profile(userId)));

        // when
        profileService.createDefaultProfile(userId);

        // then
        then(profileRepository).should(never()).save(any());
    }
}
