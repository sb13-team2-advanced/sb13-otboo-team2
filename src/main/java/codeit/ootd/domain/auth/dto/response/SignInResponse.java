package codeit.ootd.domain.auth.dto.response;

import codeit.ootd.domain.user.dto.response.UserDto;

public record SignInResponse(
        UserDto userDto,
        String accessToken
) {
    public static SignInResponse from(SignInResult result) {
        return new SignInResponse(
                result.userDto(),
                result.accessToken()
        );
    }
}