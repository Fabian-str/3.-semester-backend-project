package app.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pokemon_instances")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PokemonInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int pokemonId;

    private String nickname;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(nullable = false)
    private boolean shiny;

    @Column(nullable = false)
    private boolean currentlyOwned;

    @Column(nullable = false)
    private LocalDateTime obtainedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playthrough_id", nullable = false)
    private Playthrough playthrough;

    @PrePersist
    public void prePersist() {
        if (obtainedAt == null) {
            obtainedAt = LocalDateTime.now();
        }
    }
}