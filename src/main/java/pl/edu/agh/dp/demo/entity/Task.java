package pl.edu.agh.dp.demo.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.*;

import java.time.LocalDate;

/**
 * Zadanie w projekcie.
 * 
 * Demonstruje:
 * - Many-to-One (project, assignee)
 * - Self-reference (parentTask/subtasks)
 */
@Getter
@Setter
@NoArgsConstructor
public class Task {
    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(defaultValue = "TODO")
    private String status;

    @Column(defaultValue = "MEDIUM")
    private String priority;

    private LocalDate dueDate;

    private Integer estimatedHours;
}
