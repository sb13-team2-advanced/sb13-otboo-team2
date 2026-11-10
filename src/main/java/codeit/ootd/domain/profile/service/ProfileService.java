package codeit.ootd.domain.profile.service;

import codeit.ootd.domain.profile.dto.request.ProfileUpdateRequest;
import codeit.ootd.domain.profile.dto.response.ProfileDto;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ProfileService {

    /**
     * 사용자의 기본 프로필을 만든다. 이미 있으면 새로 만들지 않는다.
     * 회원가입 처리 후 호출한다.
     */
    void createDefaultProfile(UUID userId);

    /**
     * 프로필을 조회한다. 프로필이 아직 없으면 저장하지 않고 이름만 채운 빈 프로필을 돌려준다.
     */
    ProfileDto getProfile(UUID userId);

    /**
     * 프로필을 수정한다. 본인만 수정할 수 있다. 프로필이 없으면 만들고, 이름은 사용자 정보에 반영한다.
     */
    ProfileDto updateProfile(UUID currentUserId, UUID userId, ProfileUpdateRequest request, MultipartFile image);
}
