package codeit.ootd.domain.weather.service;

import static org.assertj.core.api.Assertions.assertThat;

import codeit.ootd.domain.weather.service.WeatherChangeDetector.PrecipitationType;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WeatherNotificationMessageBuilderTest {

    private final WeatherNotificationMessageBuilder builder =
        new WeatherNotificationMessageBuilder();

    @ParameterizedTest
    @DisplayName("기온과 강수 변화 조합에 따라 올바른 알림 문구를 생성한다")
    @CsvSource({
        "true, false, NONE, '앞으로 6시간 이내 예상 기온이 크게 높아졌어요. 옷차림을 확인해 보세요.'",
        "false, true, NONE, '앞으로 6시간 이내 예상 기온이 크게 낮아졌어요. 옷차림을 확인해 보세요.'",
        "true, true, NONE, '앞으로 6시간 이내 예상 기온이 크게 달라졌어요. 옷차림을 확인해 보세요.'",
        "false, false, RAIN, '앞으로 6시간 이내 비가 올 가능성이 있어요. 우산을 준비하세요.'",
        "false, false, SNOW, '앞으로 6시간 이내 눈이 올 가능성이 있어요. 눈에 대비하세요.'",
        "false, false, RAIN-SNOW, '앞으로 6시간 이내 비가 온 뒤 눈이 올 가능성이 있어요. 비와 눈에 대비하세요.'",
        "false, false, SNOW-RAIN, '앞으로 6시간 이내 눈이 온 뒤 비가 올 가능성이 있어요. 비와 눈에 대비하세요.'",
        "true, false, RAIN, '앞으로 6시간 이내 비가 올 가능성이 있고, 예상 기온이 크게 높아졌어요. 우산을 준비하고 옷차림도 확인해 보세요.'",
        "false, true, SNOW, '앞으로 6시간 이내 눈이 올 가능성이 있고, 예상 기온이 크게 낮아졌어요. 눈에 대비하고 옷차림도 확인해 보세요.'",
        "true, true, SNOW-RAIN, '앞으로 6시간 이내 눈이 온 뒤 비가 올 가능성이 있고, 예상 기온이 크게 달라졌어요. 비와 눈에 대비하고 옷차림도 확인해 보세요.'",
        "false, false, MIXED, '앞으로 6시간 이내 비와 눈이 함께 올 가능성이 있어요. 비와 눈에 대비하세요.'",
        "true, false, MIXED, '앞으로 6시간 이내 비와 눈이 함께 올 가능성이 있고, 예상 기온이 크게 높아졌어요. 비와 눈에 대비하고 옷차림도 확인해 보세요.'",
        "false, false, RAIN-MIXED, '앞으로 6시간 이내 비와 눈이 함께 올 가능성이 있어요. 비와 눈에 대비하세요.'"
    })
    void buildReturnsExpectedMessage(
        boolean temperatureUp,
        boolean temperatureDown,
        String precipitationOrder,
        String expected
    ) {
        Set<PrecipitationType> precipitationTypes = new LinkedHashSet<>();

        for (String type : precipitationOrder.split("-")) {
            if (!type.equals("NONE")) {
                precipitationTypes.add(PrecipitationType.valueOf(type));
            }
        }

        String actual = builder.build(
            temperatureUp,
            temperatureDown,
            precipitationTypes
        );

        assertThat(actual).isEqualTo(expected);
    }
}