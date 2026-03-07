package turtle.identity.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "client_profile")
public class ClientProfile extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    public AppUser user;

    @Column(columnDefinition = "TEXT")
    public String description;

    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<ClientSocialLink> socialLinks = new ArrayList<>();

    public static Optional<ClientProfile> findByUserId(Long userId) {
        return find("user.id", userId).firstResultOptional();
    }
}
