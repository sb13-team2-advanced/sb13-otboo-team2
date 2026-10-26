package codeit.ootd.domain.profile.service.impl;

import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.profile.service.ProfileService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final ProfileRepository profileRepository;

    @Override
    @Transactional
    public void createDefaultProfile(UUID userId) {
        profileRepository.findByUserId(userId)
            .orElseGet(() -> profileRepository.save(new Profile(userId)));
    }
}
