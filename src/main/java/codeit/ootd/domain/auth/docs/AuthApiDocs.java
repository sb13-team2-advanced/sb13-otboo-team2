
package codeit.ootd.domain.auth.docs;

import codeit.ootd.domain.auth.dto.request.ResetPasswordRequest;
import codeit.ootd.domain.auth.dto.request.SignInRequest;
import codeit.ootd.domain.auth.dto.response.SignInResponse;
import codeit.ootd.domain.auth.dto.response.TokenRefreshResponse;
import codeit.ootd.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;

@Tag(name = "인증 관리", description = "인증 관련 API")
public interface AuthApiDocs {

    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호를 입력하여 로그인합니다.",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "multipart/form-data",
                            schema = @Schema(
                                    implementation = SignInSwaggerRequest.class
                            )
                    )
            )
    )
    @ApiResponse(
            responseCode = "200",
            description = "로그인 성공",
            content = @Content(
                    schema = @Schema(implementation = SignInResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "401",
            description = "로그인 실패",
            content = @Content(
                    mediaType = "*/*",
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    ResponseEntity<SignInResponse> signIn(SignInRequest request);

    @Operation(
            summary = "비밀번호 초기화",
            description = "임시 비밀번호로 초기화 후 이메일로 전송합니다."
    )
    @ApiResponse(
            responseCode = "204",
            description = "비밀번호 초기화 성공",
            content = @Content
    )
    @ApiResponse(
            responseCode = "404",
            description = "비밀번호 초기화 실패(사용자 없음)",
            content = @Content(
                    mediaType = "*/*",
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "429",
            description = "비밀번호 초기화 요청 횟수 초과"
    )
    ResponseEntity<Void> resetPassword(
            ResetPasswordRequest request,
            @Parameter(hidden = true) HttpServletRequest httpRequest
    );

    @Operation(
            summary = "CSRF 토큰 조회",
            description = "CSRF 토큰을 발급합니다. 토큰은 XSRF-TOKEN 쿠키에 저장됩니다."
    )
    @ApiResponse(
            responseCode = "204",
            description = "CSRF 토큰 발급 성공",
            content = @Content
    )
    @ApiResponse(
            responseCode = "401",
            description = "인증 실패",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    ResponseEntity<Void> csrf(
            @Parameter(hidden = true) CsrfToken csrfToken
    );

    @Operation(
            summary = "토큰 재발급",
            description = "쿠키에 저장된 리프레시 토큰으로 리프레시 토큰과 액세스 토큰을 재발급합니다."
    )
    @ApiResponse(
            responseCode = "200",
            description = "토큰 재발급 성공",
            content = @Content(
                    mediaType = "*/*",
                    schema = @Schema(implementation = TokenRefreshResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "401",
            description = "토큰 재발급 실패",
            content = @Content(
                    mediaType = "*/*",
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    ResponseEntity<TokenRefreshResponse> refresh(
            @Parameter(
                    name = "REFRESH_TOKEN",
                    description = "리프레시 토큰",
                    required = true
            )
            String refreshToken
    );

    @Operation(
            summary = "로그아웃",
            description = "로그아웃 합니다."
    )
    @ApiResponse(
            responseCode = "204",
            description = "로그아웃 성공"
    )
    @ApiResponse(
            responseCode = "401",
            description = "로그아웃 실패",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    ResponseEntity<Void> signOut(
            @Parameter(hidden = true) String refreshToken);
}
