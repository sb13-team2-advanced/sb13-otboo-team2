package codeit.ootd.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.infra.weather.H3Service;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherNotificationRecipientFinderTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private H3Service h3Service;

    @InjectMocks
    private WeatherNotificationRecipientFinder recipientFinder;

    @Test
    @DisplayName("날씨가 변경된 H3 셀에 속한 사용자만 알림 대상으로 조회한다")
    void findRecipientIdsReturnsUsersInSameCell() {
        String targetCellId = "target-cell";

        Profile first = new Profile(UUID.randomUUID());
        first.updateLocation(37.5, 127.0);

        Profile second = new Profile(UUID.randomUUID());
        second.updateLocation(37.6, 127.1);

        Profile third = new Profile(UUID.randomUUID());
        third.updateLocation(35.1, 129.0);

        when(profileRepository.findByLatitudeIsNotNullAndLongitudeIsNotNull())
            .thenReturn(List.of(first, second, third));

        when(h3Service.toCellId(37.5, 127.0)).thenReturn(targetCellId);
        when(h3Service.toCellId(37.6, 127.1)).thenReturn(targetCellId);
        when(h3Service.toCellId(35.1, 129.0)).thenReturn("other-cell");

        Set<UUID> result = recipientFinder.findRecipientIds(targetCellId);

        assertThat(result).containsExactlyInAnyOrder(
            first.getUserId(),
            second.getUserId()
        );
    }

    @Test
    @DisplayName("위치가 등록된 프로필이 없으면 알림 대상이 없다")
    void findRecipientIdsReturnsEmptyWhenNoProfilesHaveLocation() {
        when(profileRepository.findByLatitudeIsNotNullAndLongitudeIsNotNull())
            .thenReturn(List.of());

        Set<UUID> result = recipientFinder.findRecipientIds("target-cell");

        assertThat(result).isEmpty();
        verifyNoInteractions(h3Service);
    }

    @Test
    @DisplayName("위치가 등록되어 있어도 다른 H3 셀이면 알림 대상에서 제외한다")
    void findRecipientIdsExcludesDifferentCells() {
        Profile profile = new Profile(UUID.randomUUID());
        profile.updateLocation(35.1, 129.0);

        when(profileRepository.findByLatitudeIsNotNullAndLongitudeIsNotNull())
            .thenReturn(List.of(profile));

        when(h3Service.toCellId(35.1, 129.0))
            .thenReturn("other-cell");

        Set<UUID> result = recipientFinder.findRecipientIds("target-cell");

        assertThat(result).isEmpty();

        verify(h3Service).toCellId(35.1, 129.0);
    }
}