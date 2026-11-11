package codeit.ootd.domain.user.entity;

import codeit.ootd.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_email",
                columnNames = "email"
        )
)
public class User extends BaseTimeEntity {

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Column(nullable = false)
    private boolean locked;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "temporary_password", length = 255)
    private String temporaryPassword;

    @Column(name = "temporary_password_expires_at")
    private Instant temporaryPasswordExpiresAt;

    public static User create(
            String name,
            String email,
            String encodedPassword
    ) {
        User user = new User();

        user.name = name;
        user.email = email;
        user.password = encodedPassword;

        user.role = UserRole.USER;
        user.locked = false;
        user.status = UserStatus.ACTIVE;

        return user;
    }

    public void changeName(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    public void lock() {
        this.locked = true;
    }

    public void deactivate() {
        this.status = UserStatus.DEACTIVATED;
    }

    public void markDeleted() {
        this.status = UserStatus.DELETED;
    }

    public void updateRole(UserRole role) {
        this.role = Objects.requireNonNull(role, "role은 필수입니다.");
    }

    public void unlock() {
        this.locked = false;
    }

    public void changePassword(String encodedPassword) {
        this.password = Objects.requireNonNull(
                encodedPassword,
                "encodedPassword는 필수입니다."
        );
    }

    public void issueTemporaryPassword(
            String encodedPassword,
            Instant expiresAt
    ) {
        this.temporaryPassword = Objects.requireNonNull(
                encodedPassword,
                "encodedPassword는 필수입니다."
        );
        this.temporaryPasswordExpiresAt = Objects.requireNonNull(
                expiresAt,
                "expiresAt은 필수입니다."
        );
    }

    public void clearTemporaryPassword() {
        this.temporaryPassword = null;
        this.temporaryPasswordExpiresAt = null;
    }

    public boolean hasValidTemporaryPassword(Instant now) {
        Objects.requireNonNull(now, "now는 필수입니다.");

        return temporaryPassword != null
                && temporaryPasswordExpiresAt != null
                && now.isBefore(temporaryPasswordExpiresAt);
    }



}

