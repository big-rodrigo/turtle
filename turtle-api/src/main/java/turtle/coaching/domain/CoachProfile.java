package turtle.coaching.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import turtle.identity.domain.AppUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "coach_profile")
public class CoachProfile extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    public AppUser user;

    @Column(columnDefinition = "TEXT")
    public String description;

    @Column(length = 200)
    public String specialty;

    @Column(name = "picture_url", columnDefinition = "TEXT")
    public String pictureUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public CoachStatus status = CoachStatus.PENDING;

    @OneToMany(mappedBy = "coach", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<CoachSocialLink> socialLinks = new ArrayList<>();

    public static Optional<CoachProfile> findByUserId(Long userId) {
        return find("user.id", userId).firstResultOptional();
    }
}
