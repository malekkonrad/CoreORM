package pl.edu.agh.dp.demo.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pracownik - dziedziczy z Person (JOINED).
 * 
 * Demonstruje:
 * - Dziedziczenie JOINED (extends Person)
 * - Self-reference (manager/subordinates)
 * - Many-to-One (department)
 * - Many-to-Many (projects, skills)
 * - One-to-Many (managedProjects)
 */
@Getter
@Setter
@NoArgsConstructor
public class Employee extends Person {

    @Column(nullable = false, unique = true)
    private String employeeCode;

    private LocalDate hireDate;

    private BigDecimal salary;

    private String position;

    // ==================== SELF-REFERENCE ====================
    @OneToMany(mappedBy = "manager")
    @JoinColumn(joinColumns = {"subordinates"})
    private List<Employee> subordinates = new ArrayList<>();

    @ManyToOne(mappedBy = "subordinates")
    @JoinColumn(joinColumns = {"manager"}, nullable = true)
    private Employee manager;

    // ==================== MANY-TO-ONE ====================
    @ManyToOne
    private Department department;
}
