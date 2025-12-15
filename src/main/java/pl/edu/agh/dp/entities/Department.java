package pl.edu.agh.dp.entities;

import pl.edu.agh.dp.api.annotations.Id;
import pl.edu.agh.dp.api.annotations.OneToMany;

import java.util.List;

public class Department {
    @Id
    private int id;

    @OneToMany
    private List<Employee> employees;
}
