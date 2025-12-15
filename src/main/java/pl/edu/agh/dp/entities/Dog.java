package pl.edu.agh.dp.entities;

import pl.edu.agh.dp.api.annotations.Entity;
import pl.edu.agh.dp.api.annotations.Id;

@Entity
public class Dog extends Animal {
    @Id
    Long tempID;

    int age;
    String color;
}
