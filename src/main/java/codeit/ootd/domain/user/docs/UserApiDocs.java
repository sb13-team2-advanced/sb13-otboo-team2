package codeit.ootd.domain.user.docs;

import codeit.ootd.domain.user.dto.request.UserCreateRequest;
import codeit.ootd.domain.user.dto.request.UserLockUpdateRequest;
import codeit.ootd.domain.user.dto.request.UserPasswordUpdateRequest;
import codeit.ootd.domain.user.dto.request.UserRoleUpdateRequest;
import codeit.ootd.domain.user.dto.response.UserDto;
import codeit.ootd.domain.user.dto.response.UserListResponse;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.UUID;

@Tag(name = "프로필 관리", description = "프로필 관련 API")
public interface UserApiDocs {


    @Operation(
            summary = "사용자 등록(회원가입)",
            description = "사용자 등록(회원가입) API",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(
                                    implementation = UserCreateRequest.class
                            ),
                            examples = @ExampleObject(
                                    value = """
                                        {
                                          "name": "string",
                                          "email": "string",
                                          "password": "string"
                                        }
                                        """
                            )
                    )
            )
    )
    @ApiResponse(
            responseCode = "201",
            description = "사용자 등록(회원가입) 성공",
            content = @Content(
                    mediaType = "*/*",
                    schema = @Schema(implementation = UserDto.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "사용자 등록(회원가입) 실패",
            content = @Content(
                    mediaType = "*/*",
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    ResponseEntity<UserDto> signup(
            @Valid UserCreateRequest request
    );

    @Operation(
            summary = "계정 목록 조회",
            description = "계정 목록 조회 API")
    @ApiResponse(
            responseCode = "200",
            description = "계정 목록 조회 성공",
            content = @Content(
                    mediaType = "*/*",
                    schema = @Schema(implementation = UserListResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "400",
            description = "계정 목록 조회 실패",
            content = @Content(
                    mediaType = "*/*",
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    ResponseEntity<UserListResponse> getUsers(
            String cursor,
            UUID idAfter,
            @Min(value = 1, message = "limit는 1 이상이어야 합니다.")
            int limit,

            @Parameter(
                    schema = @Schema(
                            allowableValues = {"email", "createdAt"}
                    )
            )
            String sortBy,

            @Parameter(
                    schema = @Schema(
                            allowableValues = {"ASCENDING", "DESCENDING"}
                    )
            )
            String sortDirection,

            String emailLike,
            UserRole roleEqual,
            Boolean locked
    );

    @Operation(
            summary = "권한 수정",
            description = "권한 수정 API" )
    @ApiResponse(
            responseCode = "200",
            description = "권한 수정 성공",
            content = @Content(
                    mediaType = "*/*",
                    schema = @Schema(implementation = UserListResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "권한 수정 실패(사용자 없음)",
            content = @Content(
                    mediaType = "*/*",
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    ResponseEntity<UserDto> updateRole(
            UUID userId,
            @Valid UserRoleUpdateRequest request
    );

    @Operation(
            summary = "계정 잠금 상태 변경",
            description = "[어드민 기능] 계정 잠금 상태를 변경합니다.")
    @ApiResponse(
            responseCode = "200",
            description = "계정 잠금 상태 변경 성공",
            content = @Content
    )
    @ApiResponse(
            responseCode = "404",
            description = "계정 잠금 상태 변경 실패(사용자 없음)",
            content = @Content(
                    mediaType = "*/*",
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    ResponseEntity<UserDto> updateLock(
            UUID userId,
            @Valid UserLockUpdateRequest request
    );

    @Operation(
            summary = "비밀번호 변경",
            description = "비밀번호를 변경합니다."
    )
    @ApiResponse(
            responseCode = "204",
            description = "비밀번호 변경 성공",
            content = @Content
    )
    @ApiResponse(
            responseCode = "400",
            description = "비밀번호 변경 실패(잘못된 요청)",
            content = @Content(
                    mediaType = "*/*",
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "404",
            description = "비밀번호 변경 실패(사용자 없음)",
            content = @Content(
                    mediaType = "*/*",
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    ResponseEntity<Void> updatePassword(
            @Parameter(hidden = true) UUID userId,
            @Valid UserPasswordUpdateRequest request,
            @Parameter(hidden = true) Authentication authentication
    );

}
