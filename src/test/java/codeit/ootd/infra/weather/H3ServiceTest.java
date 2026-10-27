package codeit.ootd.infra.weather;

import static org.assertj.core.api.Assertions.assertThat;

import com.uber.h3core.util.LatLng;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}