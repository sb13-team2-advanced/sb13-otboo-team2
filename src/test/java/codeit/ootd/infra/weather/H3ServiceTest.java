package codeit.ootd.infra.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import codeit.ootd.global.exception.ErrorCode;
import codeit.ootd.global.exception.OtbooException;
import com.uber.h3core.util.LatLng;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class H3ServiceTest {

    @Test
    @DisplayName("동일한 GPS 좌표는 동일한 H3 셀 ID로 변환된다")
    void toCellIdReturnsSameCellForSameCoordinates() throws IOException {

        H3Service h3Service = new H3Service();

        double latitude = 37.5665;
        double longitude = 126.9780;

        String firstCellId = h3Service.toCellId(latitude, longitude);
        String secondCellId = h3Service.toCellId(latitude, longitude);

        assertThat(firstCellId).isNotBlank();
        assertThat(secondCellId).isEqualTo(firstCellId);
    }

    @Test
    @DisplayName("H3 셀 중심 좌표는 원래 셀에 속한다")
    void toCellCenterReturnsCoordinatesInsideSameCell() throws IOException {

        H3Service h3Service = new H3Service();

        double latitude = 37.5665;
        double longitude = 126.9780;

        String cellId = h3Service.toCellId(latitude, longitude);

        LatLng center = h3Service.toCellCenter(cellId);

        String centerCellId = h3Service.toCellId(
            center.lat,
            center.lng
        );

        assertThat(center.lat).isBetween(-90.0, 90.0);
        assertThat(center.lng).isBetween(-180.0, 180.0);

        assertThat(centerCellId).isEqualTo(cellId);
    }

    @ParameterizedTest
    @CsvSource({
        "-90.1, 127.0",
        "90.1, 127.0",
        "37.5, -180.1",
        "37.5, 180.1",
        "NaN, 127.0",
        "37.5, NaN",
        "Infinity, 127.0",
        "37.5, -Infinity"
    })
    @DisplayName("유효하지 않은 위도 또는 경도는 날씨 좌표 오류를 발생시킨다")
    void validateCoordinatesRejectsInvalidCoordinates(
        double latitude,
        double longitude
    ) throws IOException {
        H3Service h3Service = new H3Service();

        assertThatThrownBy(
            () -> h3Service.validateCoordinates(latitude, longitude)
        )
            .isInstanceOf(OtbooException.class)
            .satisfies(exception ->
                assertThat(((OtbooException) exception).getErrorCode())
                    .isEqualTo(ErrorCode.WEATHER_INVALID_COORDINATES)
            );
    }
}