package codeit.ootd.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 사용자 관리
    USER_EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),

    // 의상
    CLOTHES_ATTRIBUTE_DEFINITION_NOT_FOUND(HttpStatus.NOT_FOUND, "의상 속성 정의를 찾을 수 없습니다."),
    CLOTHES_ATTRIBUTE_DEFINITION_NAME_DUPLICATED(HttpStatus.CONFLICT, "이미 존재하는 속성 정의 이름입니다."),
    CLOTHES_ATTRIBUTE_VALUES_DUPLICATED(HttpStatus.BAD_REQUEST, "선택 가능한 값에 중복이 있습니다."),

    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    INVALID_SORT_FIELD(HttpStatus.BAD_REQUEST, "지원하지 않는 정렬 기준입니다."),

    //팔로우
    FOLLOW_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 팔로우한 사용자입니다."),

    //파일
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "업로드할 파일이 비어 있습니다."),
    FILE_INVALID_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다. (jpg, jpeg, png, gif, webp)"),
    FILE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장에 실패했습니다.");


    private final HttpStatus status;
    private final String message;
}
