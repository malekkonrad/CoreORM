package pl.edu.agh.dp.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Pracownik - samodzielna encja (bez dziedziczenia).
 * 
 * Demonstruje:
 * - Self-reference (manager/subordinates) - relacja do samego siebie
 * - Many-to-One (department) - wiele pracowników w jednym dziale
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class Employee {

    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String email;

    private String phone;

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
    @ManyToOne(mappedBy = "employees")
    @JoinColumn(joinColumns = {"department"}, nullable = true)
    private Department department;
}
