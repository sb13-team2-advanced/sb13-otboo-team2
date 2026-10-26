package codeit.ootd.domain.user.dto.request;

public record UserCreateRequest(
        String name,
        String email,
        String password
) {
}
