package codeit.ootd.domain.user.entity;

import codeit.ootd.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

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

    public void lock() {
        this.locked = true;
    }

    public void deactivate() {
        this.status = UserStatus.DEACTIVATED;
    }

    public void markDeleted() {
        this.status = UserStatus.DELETED;
    }
}

