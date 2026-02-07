package entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.core.mapping.annotations.Column;
import pl.edu.agh.dp.core.mapping.annotations.Id;
import pl.edu.agh.dp.core.mapping.annotations.Inheritance;
import pl.edu.agh.dp.core.mapping.InheritanceType;

/**
 * Klasa bazowa dla hierarchii dziedziczenia JOINED.
 * Person <- Employee, Client
 */
@Getter
@Setter
@NoArgsConstructor
//@Entity
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
