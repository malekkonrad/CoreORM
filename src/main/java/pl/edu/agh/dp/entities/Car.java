package pl.edu.agh.dp.entities;


import pl.edu.agh.dp.api.annotations.Entity;

@Entity
public class Car extends Vehicle {
    Long gearNum;
    String color;
}
