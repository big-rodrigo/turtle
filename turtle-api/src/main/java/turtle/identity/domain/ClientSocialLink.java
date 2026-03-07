package turtle.identity.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import turtle.shared.domain.SocialLinkType;

@Entity
@Table(name = "client_social_link")
public class ClientSocialLink extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    public ClientProfile client;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public SocialLinkType type;

    @Column(columnDefinition = "TEXT", nullable = false)
    public String url;

    @Column(length = 100)
    public String label;
}
