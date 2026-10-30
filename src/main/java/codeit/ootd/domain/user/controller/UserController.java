package codeit.ootd.domain.user.controller;

import codeit.ootd.domain.user.dto.request.UserCreateRequest;
import codeit.ootd.domain.user.dto.response.UserDto;
import codeit.ootd.domain.user.dto.response.UserListResponse;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserDto> signup(
            @Valid @RequestBody UserCreateRequest request
    ) {
        UserDto response = userService.signup(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<UserListResponse> getUsers(
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) UUID idAfter,
            @RequestParam int limit,
            @RequestParam String sortBy,
            @RequestParam String sortDirection,
            @RequestParam(required = false) String emailLike,
            @RequestParam(required = false) UserRole roleEqual,
            @RequestParam(required = false) Boolean locked
    ) {
        UserListResponse response = userService.getUsers(
                cursor,
                idAfter,
                limit,
                sortBy,
                sortDirection,
                emailLike,
                roleEqual,
                locked
        );

        return ResponseEntity.ok(response);
    }

}
