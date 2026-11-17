package codeit.ootd.domain.profile.service.impl;

import codeit.ootd.domain.profile.dto.request.ProfileUpdateRequest;
import codeit.ootd.domain.profile.dto.response.ProfileDto;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.exception.ProfileUserNotFoundException;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.profile.service.ProfileService;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.weather.dto.response.WeatherAPILocation;
import codeit.ootd.domain.weather.service.WeatherLocationService;
import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import codeit.ootd.infra.storage.FileStorage;
import codeit.ootd.infra.storage.TransactionalFileCleaner;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private static final String IMAGE_DIRECTORY = "profiles";

    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final FileStorage fileStorage;
    private final TransactionalFileCleaner fileCleaner;
    private final WeatherLocationService weatherLocationService;

    @Override
    @Transactional
    public void createDefaultProfile(UUID userId) {
        profileRepository.findByUserId(userId)
            .orElseGet(() -> profileRepository.save(new Profile(userId)));
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileDto getProfile(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(ProfileUserNotFoundException::new);
        Profile profile = profileRepository.findByUserId(userId).orElse(null);
        return ProfileDto.of(user, profile, toLocation(profile));
    }

    @Override
    @Transactional
    public ProfileDto updateProfile(UUID currentUserId, UUID userId, ProfileUpdateRequest request,
        MultipartFile image) {
        if (currentUserId == null) {
            throw new OtbooException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (!currentUserId.equals(userId)) {
            throw new OtbooException(ErrorCode.ACCESS_DENIED);
        }
        User user = userRepository.findById(userId).orElseThrow(ProfileUserNotFoundException::new);
        Profile profile = profileRepository.findByUserId(userId)
            .orElseGet(() -> profileRepository.save(new Profile(userId)));

        if (request.name() != null) {
            user.changeName(request.name().strip());
        }
        if (request.location() != null) {
            profile.updateLocation(request.location().latitude(), request.location().longitude());
        }
        profile.update(request.gender(), request.birthDate(), request.temperatureSensitivity());

        if (image != null && !image.isEmpty()) {
            String oldImageUrl = profile.getProfileImageUrl();
            String newImageUrl = fileStorage.upload(image, IMAGE_DIRECTORY);
            // 롤백되면 새로 올린 파일을 정리하고, 이전 파일은 커밋이 끝난 뒤에만 지운다.
            fileCleaner.deleteOnRollback(newImageUrl);
            profile.changeProfileImageUrl(newImageUrl);
            fileCleaner.deleteAfterCommit(oldImageUrl);
        }

        log.info("프로필 수정 완료: userId={}", userId);
        return ProfileDto.of(user, profile, toLocation(profile));
    }

    /**
     * 저장된 위경도로 응답용 위치를 만든다. 지명은 저장 금지 정책 때문에 매번 카카오에서 조회하고,
     * 조회에 실패해도 프로필 응답이 깨지지 않도록 지명만 비워서 돌려준다.
     */
    private WeatherAPILocation toLocation(Profile profile) {
        if (profile == null || profile.getLatitude() == null || profile.getLongitude() == null) {
            return null;
        }
        double latitude = profile.getLatitude();
        double longitude = profile.getLongitude();
        try {
            return weatherLocationService.getLocation(latitude, longitude);
        } catch (RuntimeException e) {
            log.warn("지명 조회 실패, 좌표만 반환: userId={}, reason={}", profile.getUserId(), e.toString());
            return new WeatherAPILocation(latitude, longitude, latitude, longitude, List.of());
        }
    }
}
