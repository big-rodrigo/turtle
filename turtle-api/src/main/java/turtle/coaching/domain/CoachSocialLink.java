package turtle.coaching.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import turtle.shared.domain.SocialLinkType;

@Entity
@Table(name = "coach_social_link")
public class CoachSocialLink extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coach_id", nullable = false)
    public CoachProfile coach;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public SocialLinkType type;

    @Column(columnDefinition = "TEXT", nullable = false)
    public String url;

    @Column(length = 100)
    public String label;
}
