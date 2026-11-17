package codeit.ootd.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import codeit.ootd.domain.notification.dto.response.NotificationDto;
import codeit.ootd.domain.notification.entity.Notification;
import codeit.ootd.domain.notification.enums.NotificationLevel;
import codeit.ootd.domain.notification.repository.NotificationRepository;
import codeit.ootd.domain.notification.service.NotificationSseService;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.weather.entity.WeatherForecast;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherForecastRepository;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import codeit.ootd.infra.weather.H3Service;
import codeit.ootd.infra.weather.dto.OwmForecastResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class WeatherNotificationIntegrationTest {

    @Autowired
    private WeatherForecastService weatherForecastService;

    @Autowired
    private WeatherForecastRepository forecastRepository;

    @Autowired
    private WeatherLocationRepository locationRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private H3Service h3Service;

    @Autowired
    private Clock clock;

    @MockitoBean
    private NotificationSseService notificationSseService;

    private UUID recipientId;
    private UUID otherUserId;
    private UUID locationId;
    private UUID forecastId;

    @AfterEach
    void tearDown() {
        if (recipientId != null && otherUserId != null) {
            List<Notification> notifications = notificationRepository.findAll()
                .stream()
                .filter(notification ->
                    notification.getReceiverId().equals(recipientId)
                        || notification.getReceiverId().equals(otherUserId))
                .toList();

            notificationRepository.deleteAll(notifications);
        }

        if (forecastId != null) {
            forecastRepository.deleteById(forecastId);
        }

        if (locationId != null) {
            locationRepository.deleteById(locationId);
        }

        if (recipientId != null) {
            profileRepository.findByUserId(recipientId)
                .ifPresent(profileRepository::delete);
            userRepository.deleteById(recipientId);
        }

        if (otherUserId != null) {
            profileRepository.findByUserId(otherUserId)
                .ifPresent(profileRepository::delete);
            userRepository.deleteById(otherUserId);
        }
    }

    @Test
    @DisplayName("날씨 변경 시 같은 H3 지역의 사용자에게만 알림을 저장하고 SSE로 전달한다")
    void sendsWeatherNotificationToUsersInSameCell() {
        User recipient = userRepository.saveAndFlush(
            User.create(
                "알림 대상",
                UUID.randomUUID() + "@example.com",
                "encoded-password"
            )
        );
        recipientId = recipient.getId();

        User otherUser = userRepository.saveAndFlush(
            User.create(
                "다른 지역 사용자",
                UUID.randomUUID() + "@example.com",
                "encoded-password"
            )
        );
        otherUserId = otherUser.getId();

        Profile recipientProfile = new Profile(recipientId);
        recipientProfile.updateLocation(37.5665, 126.9780);
        profileRepository.saveAndFlush(recipientProfile);

        Profile otherProfile = new Profile(otherUserId);
        otherProfile.updateLocation(35.1796, 129.0756);
        profileRepository.saveAndFlush(otherProfile);

        Instant now = clock.instant();
        Instant forecastAt = now.plus(Duration.ofHours(1))
            .truncatedTo(ChronoUnit.SECONDS);

        WeatherLocation location = locationRepository.saveAndFlush(
            new WeatherLocation(
                h3Service.toCellId(37.5665, 126.9780),
                37.5665,
                126.9780,
                now
            )
        );
        locationId = location.getId();

        WeatherForecast previous = forecastRepository.saveAndFlush(
            new WeatherForecast(
                locationId,
                forecastAt,
                now.minus(Duration.ofHours(1)),
                20.0,
                (short) 65,
                800,
                0.2,
                0.0,
                0.0,
                2.5
            )
        );
        forecastId = previous.getId();

        OwmForecastResponse response = new OwmForecastResponse(
            List.of(
                new OwmForecastResponse.ForecastItem(
                    forecastAt.getEpochSecond(),
                    new OwmForecastResponse.Main(26.0, (short) 65),
                    List.of(new OwmForecastResponse.WeatherCondition(500)),
                    0.8,
                    null,
                    null,
                    new OwmForecastResponse.Wind(2.5)
                )
            )
        );

        weatherForecastService.saveForecasts(location, response);

        List<Notification> notifications = notificationRepository.findAll()
            .stream()
            .filter(notification ->
                notification.getReceiverId().equals(recipientId)
                    || notification.getReceiverId().equals(otherUserId))
            .toList();

        assertThat(notifications).hasSize(1);

        Notification notification = notifications.get(0);

        assertThat(notification.getReceiverId()).isEqualTo(recipientId);
        assertThat(notification.getTitle()).isEqualTo("날씨 예보가 달라졌어요!");
        assertThat(notification.getContent()).contains("비가 올", "높아졌어요");
        assertThat(notification.getLevel()).isEqualTo(NotificationLevel.INFO);

        verify(notificationSseService).send(
            eq(recipientId),
            any(NotificationDto.class)
        );

        verify(notificationSseService, never()).send(
            eq(otherUserId),
            any(NotificationDto.class)
        );

        WeatherForecast updated = forecastRepository.findById(forecastId)
            .orElseThrow();

        assertThat(updated.getTemperature()).isEqualTo(26.0);
        assertThat(updated.getWeatherCode()).isEqualTo(500);
    }
}
