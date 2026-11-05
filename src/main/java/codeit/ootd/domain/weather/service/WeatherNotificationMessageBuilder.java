package codeit.ootd.domain.weather.service;

import codeit.ootd.domain.weather.service.WeatherChangeDetector.PrecipitationType;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WeatherNotificationMessageBuilder {

    public String build(
        boolean temperatureUp,
        boolean temperatureDown,
        Set<PrecipitationType> precipitationTypes
    ) {
        String temperatureMessage = getTemperatureMessage(
            temperatureUp, temperatureDown
        );

        String precipitationMessage = getPrecipitationMessage(
            precipitationTypes
        );

        if (precipitationMessage.isEmpty()) {
            return "앞으로 6시간 이내 " + temperatureMessage
                + ". 옷차림을 확인해 보세요.";
        }

        boolean hasRain = precipitationTypes.contains(PrecipitationType.RAIN)
            || precipitationTypes.contains(PrecipitationType.MIXED);

        boolean hasSnow = precipitationTypes.contains(PrecipitationType.SNOW)
            || precipitationTypes.contains(PrecipitationType.MIXED);

        String advice = hasRain && hasSnow
            ? "비와 눈에 대비"
            : hasRain ? "우산을 준비" : "눈에 대비";

        if (temperatureMessage.isEmpty()) {
            return "앞으로 6시간 이내 " + precipitationMessage
                + " 가능성이 있어요. " + advice + "하세요.";
        }

        return "앞으로 6시간 이내 " + precipitationMessage
            + " 가능성이 있고, " + temperatureMessage
            + ". " + advice + "하고 옷차림도 확인해 보세요.";
    }

    private String getTemperatureMessage(
        boolean temperatureUp,
        boolean temperatureDown
    ) {
        if (temperatureUp && temperatureDown) {
            return "예상 기온이 크게 달라졌어요";
        }
        if (temperatureUp) {
            return "예상 기온이 크게 높아졌어요";
        }
        if (temperatureDown) {
            return "예상 기온이 크게 낮아졌어요";
        }
        return "";
    }

    private String getPrecipitationMessage(
        Set<PrecipitationType> types
    ) {
        if (types.isEmpty()) {
            return "";
        }

        if (types.contains(PrecipitationType.MIXED)) {
            return "비와 눈이 함께 올";
        }

        if (types.size() == 1) {
            return types.contains(PrecipitationType.RAIN)
                ? "비가 올"
                : "눈이 올";
        }

        return types.iterator().next() == PrecipitationType.RAIN
            ? "비가 온 뒤 눈이 올"
            : "눈이 온 뒤 비가 올";
    }
}
