package codeit.ootd.domain.profile.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import codeit.ootd.domain.profile.dto.request.ProfileLocationRequest;
import codeit.ootd.domain.profile.dto.request.ProfileUpdateRequest;
import codeit.ootd.domain.profile.dto.response.ProfileDto;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.enums.Gender;
import codeit.ootd.domain.profile.exception.ProfileUserNotFoundException;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.weather.dto.response.WeatherAPILocation;
import codeit.ootd.domain.weather.service.WeatherLocationService;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import codeit.ootd.infra.storage.FileStorage;
import codeit.ootd.infra.storage.TransactionalFileCleaner;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private WeatherLocationService weatherLocationService;

    private ProfileServiceImpl profileService;

    @BeforeEach
    void setUp() {
        // 서비스가 등록하는 커밋·롤백 후 처리를 직접 실행해 볼 수 있도록 트랜잭션 동기화를 켜 둔다.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.initSynchronization();
        profileService = new ProfileServiceImpl(profileRepository, userRepository, fileStorage,
                new TransactionalFileCleaner(fileStorage), weatherLocationService);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ---------- 기본 프로필 생성 ----------

    @Test
    void 프로필이_없으면_기본_프로필을_저장한다() {
        // given
        given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // when
        profileService.createDefaultProfile(USER_ID);

        // then
        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        then(profileRepository).should().save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void 이미_프로필이_있으면_새로_저장하지_않는다() {
        // given
        given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(new Profile(USER_ID)));

        // when
        profileService.createDefaultProfile(USER_ID);

        // then
        then(profileRepository).should(never()).save(any());
    }

    // ---------- 조회 ----------

    @Test
    void 조회할_사용자가_없으면_예외가_난다() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getProfile(USER_ID))
                .isInstanceOf(ProfileUserNotFoundException.class);
    }

    @Test
    void 프로필이_없으면_저장하지_않고_이름만_담아_돌려준다() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("완이")));
        given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        ProfileDto dto = profileService.getProfile(USER_ID);

        assertThat(dto.userId()).isEqualTo(USER_ID);
        assertThat(dto.name()).isEqualTo("완이");
        assertThat(dto.gender()).isNull();
        assertThat(dto.birthDate()).isNull();
        assertThat(dto.location()).isNull();
        assertThat(dto.temperatureSensitivity()).isNull();
        assertThat(dto.profileImageUrl()).isNull();
        verify(profileRepository, never()).save(any());
    }

    @Test
    void 프로필이_있으면_저장된_값을_담아_돌려준다() {
        Profile profile = new Profile(USER_ID);
        profile.update(Gender.FEMALE, LocalDate.of(2000, 1, 2), 3);
        profile.changeProfileImageUrl("/files/profiles/a.png");
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("완이")));
        given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));

        ProfileDto dto = profileService.getProfile(USER_ID);

        assertThat(dto.name()).isEqualTo("완이");
        assertThat(dto.gender()).isEqualTo(Gender.FEMALE);
        assertThat(dto.birthDate()).isEqualTo(LocalDate.of(2000, 1, 2));
        assertThat(dto.temperatureSensitivity()).isEqualTo(3);
        assertThat(dto.profileImageUrl()).isEqualTo("/files/profiles/a.png");
    }

    // ---------- 수정 ----------

    @Test
    void 수정할_사용자가_없으면_예외가_나고_아무것도_저장하지_않는다() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.updateProfile(USER_ID, USER_ID,
                new ProfileUpdateRequest("새 이름", null, null, null, null), image()))
                .isInstanceOf(ProfileUserNotFoundException.class);

        verify(profileRepository, never()).save(any());
        verifyNoInteractions(fileStorage);
    }

    @Test
    void 프로필이_없으면_새로_만들어_값을_저장하고_이름은_사용자에_반영한다() {
        User user = user("완이");
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(profileRepository.save(any(Profile.class))).willAnswer(invocation -> invocation.getArgument(0));

        ProfileDto dto = profileService.updateProfile(USER_ID, USER_ID,
                new ProfileUpdateRequest(" 새 이름 ", Gender.MALE, LocalDate.of(1999, 5, 6), null, 4), null);

        assertThat(user.getName()).isEqualTo("새 이름");
        assertThat(dto.name()).isEqualTo("새 이름");
        assertThat(dto.gender()).isEqualTo(Gender.MALE);
        assertThat(dto.birthDate()).isEqualTo(LocalDate.of(1999, 5, 6));
        assertThat(dto.temperatureSensitivity()).isEqualTo(4);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void 보내지_않은_값은_기존_값을_유지한다() {
        User user = user("완이");
        Profile profile = new Profile(USER_ID);
        profile.update(Gender.MALE, LocalDate.of(1999, 5, 6), 2);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));

        ProfileDto dto = profileService.updateProfile(USER_ID, USER_ID,
                new ProfileUpdateRequest(null, null, null, null, 5), null);

        assertThat(user.getName()).isEqualTo("완이");
        assertThat(dto.gender()).isEqualTo(Gender.MALE);
        assertThat(dto.birthDate()).isEqualTo(LocalDate.of(1999, 5, 6));
        assertThat(dto.temperatureSensitivity()).isEqualTo(5);
        verify(profileRepository, never()).save(any());
    }

    @Test
    void 이미지를_올리면_새_주소를_저장하고_커밋된_뒤에만_이전_이미지를_삭제한다() {
        Profile profile = new Profile(USER_ID);
        profile.changeProfileImageUrl("/files/profiles/old.png");
        MockMultipartFile image = image();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("완이")));
        given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));
        given(fileStorage.upload(image, "profiles")).willReturn("/files/profiles/new.png");

        ProfileDto dto = profileService.updateProfile(USER_ID, USER_ID,
                new ProfileUpdateRequest(null, null, null, null, null), image);

        assertThat(dto.profileImageUrl()).isEqualTo("/files/profiles/new.png");
        verify(fileStorage, never()).delete(any());

        commit();

        verify(fileStorage).delete("/files/profiles/old.png");
        verify(fileStorage, never()).delete("/files/profiles/new.png");
    }

    @Test
    void 수정이_롤백되면_새_이미지만_삭제하고_이전_이미지는_그대로_둔다() {
        Profile profile = new Profile(USER_ID);
        profile.changeProfileImageUrl("/files/profiles/old.png");
        MockMultipartFile image = image();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("완이")));
        given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));
        given(fileStorage.upload(image, "profiles")).willReturn("/files/profiles/new.png");

        profileService.updateProfile(USER_ID, USER_ID, new ProfileUpdateRequest(null, null, null, null, null), image);
        rollback();

        verify(fileStorage).delete("/files/profiles/new.png");
        verify(fileStorage, never()).delete("/files/profiles/old.png");
    }

    @Test
    void 비어_있는_이미지는_업로드하지_않는다() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("완이")));
        given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(new Profile(USER_ID)));
        MockMultipartFile empty = new MockMultipartFile("image", "a.png", "image/png", new byte[0]);

        profileService.updateProfile(USER_ID, USER_ID, new ProfileUpdateRequest(null, null, null, null, null), empty);

        verifyNoInteractions(fileStorage);
    }

    @Test
    void 이미지_저장에_실패하면_예외가_그대로_전달되고_파일은_지우지_않는다() {
        MockMultipartFile image = image();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("완이")));
        given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(new Profile(USER_ID)));
        given(fileStorage.upload(image, "profiles")).willThrow(new OtbooException(ErrorCode.FILE_INVALID_TYPE));

        assertThatThrownBy(() -> profileService.updateProfile(USER_ID, USER_ID,
                new ProfileUpdateRequest(null, null, null, null, null), image))
                .isInstanceOf(OtbooException.class);
        rollback();

        verify(fileStorage, never()).delete(any());
    }

    // ---------- 본인 확인 ----------

    @Test
    void 인증_정보가_없으면_수정할_수_없다() {
        assertThatThrownBy(() -> profileService.updateProfile(null, USER_ID,
                new ProfileUpdateRequest(null, null, null, null, null), image()))
                .isInstanceOfSatisfying(OtbooException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.AUTHENTICATION_REQUIRED));

        verifyNoInteractions(userRepository, profileRepository, fileStorage);
    }

    @Test
    void 다른_사용자의_프로필은_수정할_수_없고_파일도_올리지_않는다() {
        assertThatThrownBy(() -> profileService.updateProfile(UUID.randomUUID(), USER_ID,
                new ProfileUpdateRequest("새 이름", null, null, null, null), image()))
                .isInstanceOfSatisfying(OtbooException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));

        verifyNoInteractions(userRepository, profileRepository, fileStorage);
    }

    // ---------- 위치 ----------

    @Test
    void 위경도를_보내면_저장하고_지명을_조회해_위치로_돌려준다() {
        Profile profile = new Profile(USER_ID);
        WeatherAPILocation location = new WeatherAPILocation(37.5, 127.0, 37.5, 127.0, List.of("서울특별시", "강남구", "역삼동"));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("완이")));
        given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));
        given(weatherLocationService.getLocation(37.5, 127.0)).willReturn(location);

        ProfileDto dto = profileService.updateProfile(USER_ID, USER_ID,
                new ProfileUpdateRequest(null, null, null, new ProfileLocationRequest(37.5, 127.0), null), null);

        assertThat(profile.getLatitude()).isEqualTo(37.5);
        assertThat(profile.getLongitude()).isEqualTo(127.0);
        assertThat(dto.location()).isEqualTo(location);
    }

    @Test
    void 위치를_보내지_않으면_기존_위치를_유지한다() {
        Profile profile = new Profile(USER_ID);
        profile.updateLocation(35.1, 129.0);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("완이")));
        given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));
        given(weatherLocationService.getLocation(35.1, 129.0))
                .willReturn(new WeatherAPILocation(35.1, 129.0, 35.1, 129.0, List.of("부산광역시")));

        ProfileDto dto = profileService.updateProfile(USER_ID, USER_ID,
                new ProfileUpdateRequest(null, null, null, null, 3), null);

        assertThat(profile.getLatitude()).isEqualTo(35.1);
        assertThat(dto.location().locationNames()).containsExactly("부산광역시");
    }

    @Test
    void 저장된_좌표가_없으면_위치는_비어_있고_지명을_조회하지_않는다() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("완이")));
        given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(new Profile(USER_ID)));

        ProfileDto dto = profileService.getProfile(USER_ID);

        assertThat(dto.location()).isNull();
        verifyNoInteractions(weatherLocationService);
    }

    @Test
    void 지명_조회에_실패하면_좌표만_담고_지명은_비워서_돌려준다() {
        Profile profile = new Profile(USER_ID);
        profile.updateLocation(37.5, 127.0);
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user("완이")));
        given(profileRepository.findByUserId(USER_ID)).willReturn(Optional.of(profile));
        given(weatherLocationService.getLocation(37.5, 127.0)).willThrow(new IllegalStateException("카카오 장애"));

        ProfileDto dto = profileService.getProfile(USER_ID);

        assertThat(dto.location()).isEqualTo(new WeatherAPILocation(37.5, 127.0, 37.5, 127.0, List.of()));
    }

    // ---------- 준비 ----------

    private static User user(String name) {
        User user = User.create(name, "test@test.com", "encoded");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    private static MockMultipartFile image() {
        return new MockMultipartFile("image", "a.png", "image/png", new byte[] {1, 2, 3});
    }

    private void commit() {
        List<TransactionSynchronization> synchronizations =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private void rollback() {
        List<TransactionSynchronization> synchronizations =
                new ArrayList<>(TransactionSynchronizationManager.getSynchronizations());
        synchronizations.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }
}
