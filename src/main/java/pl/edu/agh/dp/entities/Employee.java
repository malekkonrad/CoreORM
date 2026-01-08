package pl.edu.agh.dp.entities;

import lombok.Getter;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.*;


import java.util.List;
//@Entity
@Getter
@Setter
public class Employee {
    @Id
    private Long id;
    private Double salary;

    private Integer departmentId;
//    private int versionId;

    // simple mapping
//    @ManyToOne
//    private Department department;

    // FIXME handle id on the foreign key
    // FIXME error on column annotation
    // FIXME id on foreign key
//    @OneToOne(mappedBy = "employer")
//    @JoinColumn(nullable = true)
//    private Employee employer;
//
//    // multiple join columns
//    @OneToOne(mappedBy = "employee")
//    @JoinColumn(joinColumns = {"department", "employee"})
//    private Employee employee;
//
//    // multiple join columns with non-foreign key
//    @OneToOne(mappedBy = "employee2")
//    @JoinColumn(joinColumns = {"versionId", "employee"})
//    private Employee employee2;
}
