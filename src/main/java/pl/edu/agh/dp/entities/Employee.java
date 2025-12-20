package pl.edu.agh.dp.entities;

import lombok.Getter;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.*;

import java.util.List;
//@Entity
@Getter
@Setter
public class Employee extends User {
//    @Id
//    private int id;
    private double salary;

    private int departmentId;

//    // FIXME handle id on the foreign key
//    // FIXME error on column annotation
//    @OneToOne(mappedBy = "employer")
//    @JoinColumn(nullable = true)
//    private Employee employer;
//
//    @OneToOne(mappedBy = "employee")
//    @JoinColumn(joinColumns = {"departmentId", "employee"})
//    private Employee employee;
}
