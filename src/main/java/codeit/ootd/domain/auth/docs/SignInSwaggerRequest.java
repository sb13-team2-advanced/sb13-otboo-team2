package codeit.ootd.domain.auth.docs;

import io.swagger.v3.oas.annotations.media.Schema;

public record SignInSwaggerRequest(

        @Schema(description = "이메일", type = "string")
        String username,

        @Schema(description = "비밀번호", type = "string")
        String password
) {
}
