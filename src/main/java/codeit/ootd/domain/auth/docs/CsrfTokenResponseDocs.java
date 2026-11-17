package codeit.ootd.domain.auth.docs;

import io.swagger.v3.oas.annotations.media.Schema;

public record CsrfTokenResponseDocs(

        @Schema(
                description = "CSRF 토큰",
                example = "example-csrf-token"
        )
        String token

) {
}
