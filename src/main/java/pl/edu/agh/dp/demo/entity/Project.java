package pl.edu.agh.dp.demo.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Projekt.
 * 
 * Demonstruje:
 * - Many-to-Many (members)
 * - Many-to-One (projectManager, client)
 * - One-to-Many (tasks)
 */
@Getter
@Setter
@NoArgsConstructor
public class Project {
    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    private BigDecimal budget;

    @Column(defaultValue = "PLANNING")
    private String status;
}
