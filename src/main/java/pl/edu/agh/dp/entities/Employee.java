package pl.edu.agh.dp.entities;

import pl.edu.agh.dp.api.annotations.Entity;
import pl.edu.agh.dp.api.annotations.Id;
import pl.edu.agh.dp.api.annotations.OneToOne;

import java.util.List;

public class Employee {
    @Id
    private int id;
    private double salary;

    @OneToOne(mappedBy = "employer")
    private Employee employer;

    @OneToOne(mappedBy = "employee")
    private Employee employee;
}
