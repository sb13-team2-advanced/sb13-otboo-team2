package codeit.ootd.infra.weather;

import com.uber.h3core.H3Core;
import com.uber.h3core.util.LatLng;
import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class H3Service {

    private static final int RESOLUTION = 8;

    private final H3Core h3Core;

    public H3Service() throws IOException {
        this.h3Core = H3Core.newInstance();
    }

    public String toCellId(double latitude, double longitude) {

        if (!Double.isFinite(latitude)
            || !Double.isFinite(longitude)
            || latitude < -90.0 || latitude > 90.0
            || longitude < -180.0 || longitude > 180.0) {

            throw new IllegalArgumentException("유효하지 않은 위도 또는 경도입니다.");
        }

        return h3Core.latLngToCellAddress(
            latitude,
            longitude,
            RESOLUTION
        );
    }

    public LatLng toCellCenter(String cellId) {
        return h3Core.cellToLatLng(cellId);
    }
}
