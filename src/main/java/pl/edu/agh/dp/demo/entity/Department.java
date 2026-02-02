package pl.edu.agh.dp.demo.entity;

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
 * - One-to-Many (employees)
 * - Self-reference (parentDepartment/subDepartments)
 */
@Getter
@Setter
@NoArgsConstructor
public class Department {
    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String code;

    private String description;

    // ==================== ONE-TO-MANY ====================
    @OneToMany
    private List<Employee> employees = new ArrayList<>();

    // ==================== SELF-REFERENCE ====================
    @OneToMany(mappedBy = "parentDepartment")
    @JoinColumn(joinColumns = {"subDepartments"})
    private List<Department> subDepartments = new ArrayList<>();

    @ManyToOne(mappedBy = "subDepartments")
    @JoinColumn(joinColumns = {"parentDepartment"}, nullable = true)
    private Department parentDepartment;
}
