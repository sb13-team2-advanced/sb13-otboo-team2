package codeit.ootd.domain.recommendation.integration;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import codeit.ootd.domain.auth.entity.AuthSession;
import codeit.ootd.domain.auth.jwt.JwtProvider;
import codeit.ootd.domain.auth.service.AuthSessionService;
import codeit.ootd.domain.clothes.entity.Clothes;
import codeit.ootd.domain.clothes.entity.ClothesAttribute;
import codeit.ootd.domain.clothes.entity.ClothesAttributeDefinition;
import codeit.ootd.domain.clothes.enums.ClothesType;
import codeit.ootd.domain.clothes.repository.ClothesAttributeDefinitionRepository;
import codeit.ootd.domain.clothes.repository.ClothesAttributeRepository;
import codeit.ootd.domain.clothes.repository.ClothesRepository;
import codeit.ootd.domain.profile.entity.Profile;
import codeit.ootd.domain.profile.repository.ProfileRepository;
import codeit.ootd.domain.user.entity.User;
import codeit.ootd.domain.user.repository.UserRepository;
import codeit.ootd.domain.weather.entity.Weather;
import codeit.ootd.domain.weather.entity.WeatherLocation;
import codeit.ootd.domain.weather.repository.WeatherLocationRepository;
import codeit.ootd.domain.weather.repository.WeatherRepository;
import codeit.ootd.global.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 JWT와 DB의 사용자·세션을 검증한 뒤 본인 옷장과 날씨로 추천하는 흐름을 확인한다.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recommendation-security;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecommendationSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private AuthSessionService authSessionService;
    @Autowired private UserRepository userRepository;
    @Autowired private ClothesRepository clothesRepository;
    @Autowired private ClothesAttributeRepository attributeRepository;
    @Autowired private ClothesAttributeDefinitionRepository definitionRepository;
    @Autowired private WeatherLocationRepository locationRepository;
    @Autowired private WeatherRepository weatherRepository;
    @Autowired private ProfileRepository profileRepository;

    @Test
    void JWT_사용자의_의상과_속성으로만_추천하고_다른_사용자_ID는_무시한다() throws Exception {
        // given
        User user = saveUser();
        User otherUser = saveUser();
        String token = accessToken(user.getId());
        Weather weather = saveWeather();
        Clothes top = saveClothes(user.getId(), ClothesType.TOP);
        Clothes bottom = saveClothes(user.getId(), ClothesType.BOTTOM);
        saveClothes(otherUser.getId(), ClothesType.TOP);
        saveClothes(otherUser.getId(), ClothesType.BOTTOM);
        ClothesAttributeDefinition definition = definitionRepository.save(
                new ClothesAttributeDefinition("두께", List.of("얇음", "보통", "두꺼움")));
        attributeRepository.save(new ClothesAttribute(top.getId(), definition.getId(), "얇음"));

        // when then
        mockMvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", weather.getId().toString())
                        .param("userId", otherUser.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.weatherId").value(weather.getId().toString()))
                .andExpect(jsonPath("$.clothes.length()").value(2))
                .andExpect(jsonPath("$.clothes[*].clothesId", containsInAnyOrder(
                        top.getId().toString(), bottom.getId().toString())))
                .andExpect(jsonPath("$.clothes[?(@.type == 'TOP')].attributes[0].definitionId")
                        .value(containsInAnyOrder(definition.getId().toString())))
                .andExpect(jsonPath("$.clothes[?(@.type == 'TOP')].attributes[0].value")
                        .value(containsInAnyOrder("얇음")));
    }

    @Test
    void 본인_옷장이_비면_다른_사용자의_의상을_추천하지_않는다() throws Exception {
        // given
        User user = saveUser();
        User otherUser = saveUser();
        String token = accessToken(user.getId());
        Weather weather = saveWeather();
        saveClothes(otherUser.getId(), ClothesType.TOP);
        saveClothes(otherUser.getId(), ClothesType.BOTTOM);

        // when then
        mockMvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", weather.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.clothes").isEmpty());
    }

    @Test
    void JWT가_없으면_추천을_조회할_수_없다() throws Exception {
        // given
        UUID weatherId = UUID.randomUUID();

        // when then
        mockMvc.perform(get("/api/recommendations").param("weatherId", weatherId.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorCode.AUTHENTICATION_REQUIRED.getMessage()));
    }

    @Test
    void 잘못된_JWT로는_추천을_조회할_수_없다() throws Exception {
        // given
        String token = "invalid-token";

        // when then
        mockMvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorCode.AUTHENTICATION_REQUIRED.getMessage()));
    }

    @Test
    void JWT가_유효해도_사용자가_없으면_추천을_조회할_수_없다() throws Exception {
        // given
        String token = accessToken(UUID.randomUUID());

        // when then
        mockMvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorCode.AUTHENTICATION_REQUIRED.getMessage()));
    }

    @Test
    void 폐기된_세션의_JWT로는_추천을_조회할_수_없다() throws Exception {
        // given
        User user = saveUser();
        AuthSession session = authSessionService.createSession(user.getId());
        String token = jwtProvider.generateAccessToken(user.getId(), session.getId());
        authSessionService.revokeSession(session.getId());

        // when then
        mockMvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_AUTH_SESSION.getMessage()));
    }

    @Test
    void 잠긴_사용자는_추천을_조회할_수_없다() throws Exception {
        // given
        User user = saveUser();
        String token = accessToken(user.getId());
        user.lock();

        // when then
        mockMvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorCode.ACCOUNT_LOCKED.getMessage()));
    }

    @Test
    void 비활성_사용자는_추천을_조회할_수_없다() throws Exception {
        // given
        User user = saveUser();
        String token = accessToken(user.getId());
        user.deactivate();

        // when then
        mockMvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(ErrorCode.ACCOUNT_INACTIVE.getMessage()));
    }

    @Test
    void 인증되어도_없는_날씨는_추천할_수_없다() throws Exception {
        // given
        User user = saveUser();
        String token = accessToken(user.getId());

        // when then
        mockMvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.RECOMMENDATION_WEATHER_NOT_FOUND.getMessage()));
    }

    /**
     * 인증 사용자 프로필로 두께와 아우터 선택이 바뀌며, 민감도가 없으면 기본 추천을 유지하는지 검증한다.
     */
    @ParameterizedTest
    @CsvSource({
            "21.0, 1, 보통, 3",
            "21.0, 2, 보통, 3",
            "21.0, 3, 보통, 2",
            "21.0, 4, 얇음, 2",
            "21.0, 5, 얇음, 2",
            "21.0, , 보통, 2",
            "11.0, 1, 두꺼움, 3",
            "19.0, 5, 얇음, 2"
    })
    void JWT_사용자의_온도_민감도로_의상_두께와_아우터를_추천한다(
            double temperature, Integer sensitivity, String expectedThickness, int expectedCount) throws Exception {
        // given
        User user = saveUser();
        User otherUser = saveUser();
        String token = accessToken(user.getId());
        Profile profile = new Profile(user.getId());
        profile.update(null, null, sensitivity);
        profileRepository.save(profile);
        Profile otherProfile = new Profile(otherUser.getId());
        otherProfile.update(null, null, 5);
        profileRepository.save(otherProfile);
        Weather weather = saveWeather(temperature);
        ClothesAttributeDefinition definition = definitionRepository.save(
                new ClothesAttributeDefinition("두께", List.of("얇음", "보통", "두꺼움")));
        saveClothesWithThickness(user.getId(), ClothesType.TOP, definition, "얇음");
        saveClothesWithThickness(user.getId(), ClothesType.TOP, definition, "보통");
        saveClothesWithThickness(user.getId(), ClothesType.TOP, definition, "두꺼움");
        saveClothesWithThickness(user.getId(), ClothesType.BOTTOM, definition, "얇음");
        saveClothesWithThickness(user.getId(), ClothesType.BOTTOM, definition, "보통");
        saveClothesWithThickness(user.getId(), ClothesType.BOTTOM, definition, "두꺼움");
        saveClothesWithThickness(user.getId(), ClothesType.OUTER, definition, "보통");

        // when then
        mockMvc.perform(get("/api/recommendations")
                        .header("Authorization", "Bearer " + token)
                        .param("weatherId", weather.getId().toString())
                        .param("userId", otherUser.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.weatherId").value(weather.getId().toString()))
                .andExpect(jsonPath("$.clothes.length()").value(expectedCount))
                .andExpect(jsonPath("$.clothes[?(@.type == 'TOP')].attributes[0].value")
                        .value(containsInAnyOrder(expectedThickness)))
                .andExpect(jsonPath("$.clothes[?(@.type == 'BOTTOM')].attributes[0].value")
                        .value(containsInAnyOrder(expectedThickness)));
    }

    private void saveClothesWithThickness(UUID userId, ClothesType type,
                                         ClothesAttributeDefinition definition, String thickness) {
        Clothes clothes = saveClothes(userId, type);
        attributeRepository.save(new ClothesAttribute(clothes.getId(), definition.getId(), thickness));
    }

    private User saveUser() {
        return userRepository.save(User.create("추천 사용자", UUID.randomUUID() + "@example.com", "encoded-password"));
    }

    private String accessToken(UUID userId) {
        AuthSession session = authSessionService.createSession(userId);
        return jwtProvider.generateAccessToken(userId, session.getId());
    }

    private Clothes saveClothes(UUID userId, ClothesType type) {
        return clothesRepository.save(new Clothes(userId, type.name(), type, null));
    }

    private Weather saveWeather() {
        return saveWeather(25.0);
    }

    private Weather saveWeather(double temperature) {
        Instant now = Instant.now();
        WeatherLocation location = locationRepository.save(
                new WeatherLocation("recommendation-test", 37.5, 127.0, now));
        return weatherRepository.save(new Weather(location.getId(), LocalDate.of(2026, 11, 4),
                now, temperature, temperature, temperature, (short) 50, 800, 0.0, 0.0, 0.0, 1.0, (short) 8));
    }
}
