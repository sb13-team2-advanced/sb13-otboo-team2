package codeit.ootd.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_SORT_FIELD(HttpStatus.BAD_REQUEST, "지원하지 않는 정렬 기준입니다."),

    // 사용자 관리
    USER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),

    // 인증 및 권한
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 Refresh Token입니다."),
    INVALID_AUTH_SESSION(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 인증 세션입니다."),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "잠긴 계정입니다."),
    ACCOUNT_INACTIVE(HttpStatus.FORBIDDEN, "활성화되지 않은 계정입니다."),

    // 의상
    CLOTHES_ATTRIBUTE_DEFINITION_NOT_FOUND(HttpStatus.NOT_FOUND, "의상 속성 정의를 찾을 수 없습니다."),
    CLOTHES_ATTRIBUTE_DEFINITION_NAME_DUPLICATED(HttpStatus.CONFLICT, "이미 존재하는 속성 정의 이름입니다."),
    CLOTHES_ATTRIBUTE_VALUES_DUPLICATED(HttpStatus.BAD_REQUEST, "선택 가능한 값에 중복이 있습니다."),
    CLOTHES_NOT_FOUND(HttpStatus.NOT_FOUND, "의상을 찾을 수 없습니다."),
    CLOTHES_OWNER_NOT_FOUND(HttpStatus.NOT_FOUND, "의상 소유자를 찾을 수 없습니다."),
    CLOTHES_ATTRIBUTE_VALUE_INVALID(HttpStatus.BAD_REQUEST, "선택할 수 없는 속성 값입니다."),
    CLOTHES_ATTRIBUTE_DUPLICATED(HttpStatus.BAD_REQUEST, "같은 속성 정의가 중복되어 있습니다."),
    CLOTHES_IN_USE(HttpStatus.CONFLICT, "피드에서 사용 중인 의상은 삭제할 수 없습니다."),
    CLOTHES_INVALID_CURSOR(HttpStatus.BAD_REQUEST, "커서 또는 조회 개수가 올바르지 않습니다."),

    // 팔로우
    FOLLOW_SELF_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "자기 자신을 팔로우할 수 없습니다."),
    FOLLOW_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 팔로우한 사용자입니다."),
    FOLLOW_USER_NOT_FOUND(HttpStatus.BAD_REQUEST, "팔로우 사용자를 찾을 수 없습니다."),
    FOLLOW_NOT_FOUND(HttpStatus.BAD_REQUEST, "팔로우 관계를 찾을 수 없습니다."),
    FOLLOW_INVALID_CURSOR(HttpStatus.BAD_REQUEST, "커서 또는 조회 개수가 올바르지 않습니다."),

    // 파일
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "업로드할 파일이 비어 있습니다."),
    FILE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다. (jpg, jpeg, png, gif, webp)"),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "파일 크기가 너무 큽니다."),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장에 실패했습니다."),

    // 추천
    RECOMMENDATION_WEATHER_NOT_FOUND(HttpStatus.BAD_REQUEST, "추천할 날씨를 찾을 수 없습니다.");


    private final HttpStatus status;
    private final String message;
}
