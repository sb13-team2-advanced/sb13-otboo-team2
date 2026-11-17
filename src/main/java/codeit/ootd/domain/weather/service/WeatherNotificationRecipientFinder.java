package codeit.ootd.domain.weather.service;

import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.infra.weather.H3Service;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WeatherNotificationRecipientFinder {

    private final ProfileRepository profileRepository;
    private final H3Service h3Service;

    public Set<UUID> findRecipientIds(String cellId) {
        return profileRepository
            .findByLatitudeIsNotNullAndLongitudeIsNotNull()
            .stream()
            .filter(profile -> cellId.equals(
                h3Service.toCellId(
                    profile.getLatitude(),
                    profile.getLongitude()
                )
            ))
            .map(profile -> profile.getUserId())
            .collect(Collectors.toSet());
    }
}
