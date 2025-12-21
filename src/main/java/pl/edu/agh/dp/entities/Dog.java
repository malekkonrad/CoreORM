package pl.edu.agh.dp.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.Entity;
import pl.edu.agh.dp.api.annotations.Id;

@Getter
@Setter
@NoArgsConstructor
@Entity
public class Dog extends Animal {
//    @Id
//    Long tempID;

    int age;
    String color;
}
