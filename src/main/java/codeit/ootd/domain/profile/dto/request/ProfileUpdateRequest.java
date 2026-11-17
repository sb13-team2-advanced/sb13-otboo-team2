package codeit.ootd.domain.profile.dto.request;

import codeit.ootd.domain.profile.enums.Gender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 부분 수정 요청. null인 항목은 기존 값을 유지한다.
 */
public record ProfileUpdateRequest(

        @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
        @Pattern(regexp = ".*\\S.*", message = "이름은 비어 있을 수 없습니다.")
        String name,

        Gender gender,

        @Past(message = "생년월일은 과거 날짜여야 합니다.")
        LocalDate birthDate,

        @Valid
        ProfileLocationRequest location,

        @Min(value = 1, message = "온도 민감도는 1 이상이어야 합니다.")
        @Max(value = 5, message = "온도 민감도는 5 이하여야 합니다.")
        Integer temperatureSensitivity
) {
}
