package pl.edu.agh.dp.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Departament/Dział firmy.
 * 
 * Demonstruje:
 * - One-to-Many (employees) - jeden dział ma wielu pracowników
 * - Self-reference (parentDepartment/subDepartments) - hierarchia działów
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class Department {

    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String code;

    private String description;

    // ==================== ONE-TO-MANY ====================
    @OneToMany(mappedBy = "department")
    @JoinColumn(joinColumns = {"employees"})
    private List<Employee> employees = new ArrayList<>();

    // ==================== SELF-REFERENCE ====================
    @OneToMany(mappedBy = "parentDepartment")
    @JoinColumn(joinColumns = {"subDepartments"})
    private List<Department> subDepartments = new ArrayList<>();

    @ManyToOne(mappedBy = "subDepartments")
    @JoinColumn(joinColumns = {"parentDepartment"}, nullable = true)
    private Department parentDepartment;
}
