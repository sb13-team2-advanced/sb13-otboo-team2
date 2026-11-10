package codeit.ootd.domain.user.controller;

import codeit.ootd.domain.user.dto.request.UserCreateRequest;
import codeit.ootd.domain.user.dto.request.UserLockUpdateRequest;
import codeit.ootd.domain.user.dto.request.UserPasswordUpdateRequest;
import codeit.ootd.domain.user.dto.request.UserRoleUpdateRequest;
import codeit.ootd.domain.user.dto.response.UserDto;
import codeit.ootd.domain.user.dto.response.UserListResponse;
import codeit.ootd.domain.user.entity.UserRole;
import codeit.ootd.domain.user.exception.InvalidUserQueryException;
import codeit.ootd.domain.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Slf4j
@Validated
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
            @RequestParam @Min(value = 1, message = "limit는 1 이상이어야 합니다.") int limit,
            @RequestParam String sortBy,
            @RequestParam String sortDirection,
            @RequestParam(required = false) String emailLike,
            @RequestParam(required = false) UserRole roleEqual,
            @RequestParam(required = false) Boolean locked
    ) {

        if (limit < 1 || limit > 100) {
            throw new InvalidUserQueryException();
        }

        if (!"email".equals(sortBy) && !"createdAt".equals(sortBy)) {
            throw new InvalidUserQueryException();
        }

        if (!"ASCENDING".equals(sortDirection)
                && !"DESCENDING".equals(sortDirection)) {
            throw new InvalidUserQueryException();
        }

        boolean cursorProvided = cursor != null;
        boolean idAfterProvided = idAfter != null;

        if (cursorProvided != idAfterProvided
                || (cursorProvided && cursor.isBlank())) {
            throw new InvalidUserQueryException();
        }

        if ("createdAt".equals(sortBy) && cursor != null) {
            try {
                Instant.parse(cursor);
            } catch (DateTimeParseException e) {
                throw new InvalidUserQueryException();
            }
        }

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

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{userId}/role")
    public ResponseEntity<UserDto> updateRole(
            @PathVariable UUID userId,
            @Valid @RequestBody UserRoleUpdateRequest request
    ) {
        UserDto response = userService.updateRole(
                userId,
                request.role()
        );

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{userId}/lock")
    public ResponseEntity<UserDto> updateLock(
            @PathVariable UUID userId,
            @Valid @RequestBody UserLockUpdateRequest request
    ) {
        UserDto response = userService.updateLock(
                userId,
                request.locked()
        );

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{userId}/password")
    public ResponseEntity<Void> updatePassword(
            @PathVariable UUID userId,
            @Valid @RequestBody UserPasswordUpdateRequest request,
            Authentication authentication
    ) {
        UUID authenticatedUserId = UUID.fromString(
                authentication.getName()
        );

        if (!authenticatedUserId.equals(userId)) {
            return ResponseEntity.status(403).build();
        }

        userService.updatePassword(userId, request.password());

        return ResponseEntity.noContent().build();
    }




}
