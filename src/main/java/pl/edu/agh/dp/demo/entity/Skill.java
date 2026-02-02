package pl.edu.agh.dp.demo.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.Column;
import pl.edu.agh.dp.api.annotations.Id;

/**
 * Umiejętność/kompetencja.
 * 
 * Demonstruje:
 * - Many-to-Many (employees)
 * - Self-reference (parentSkill/subSkills)
 */
@Getter
@Setter
@NoArgsConstructor
public class Skill {
    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    private String category;
}
