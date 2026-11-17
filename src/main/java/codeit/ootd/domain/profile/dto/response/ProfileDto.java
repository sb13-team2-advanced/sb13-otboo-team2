package codeit.ootd.domain.profile.dto.response;

import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.enums.Gender;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.weather.dto.response.WeatherAPILocation;
import java.time.LocalDate;
import java.util.UUID;

public record ProfileDto(
        UUID userId,
        String name,
        Gender gender,
        LocalDate birthDate,
        WeatherAPILocation location,
        Integer temperatureSensitivity,
        String profileImageUrl
) {

    /**
     * 이름은 사용자 정보를 따른다. 프로필이 아직 없으면 나머지 항목은 비어 있고, 위치는 저장된 좌표가 있을 때만 채운다.
     */
    public static ProfileDto of(User user, Profile profile, WeatherAPILocation location) {
        if (profile == null) {
            return new ProfileDto(user.getId(), user.getName(), null, null, null, null, null);
        }
        return new ProfileDto(user.getId(), user.getName(), profile.getGender(), profile.getBirthDate(), location,
                profile.getTemperatureSensitivity(), profile.getProfileImageUrl());
    }
}
