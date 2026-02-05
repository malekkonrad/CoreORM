package pl.edu.agh.dp.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.Column;
import pl.edu.agh.dp.api.annotations.Entity;
import pl.edu.agh.dp.api.annotations.Id;
import pl.edu.agh.dp.api.annotations.Inheritance;
import pl.edu.agh.dp.core.mapping.InheritanceType;

/**
 * Klasa bazowa dla hierarchii dziedziczenia JOINED.
 * Person <- Employee, Client
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class Person {

    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    private String email;
    private String phone;
}
