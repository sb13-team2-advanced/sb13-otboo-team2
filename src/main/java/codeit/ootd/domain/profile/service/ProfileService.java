package codeit.ootd.domain.profile.service;

import java.util.UUID;

public interface ProfileService {

    /**
     * 사용자의 기본 프로필을 만든다. 이미 있으면 새로 만들지 않는다.
     * 회원가입 처리 후 호출한다.
     */
    void createDefaultProfile(UUID userId);
}
