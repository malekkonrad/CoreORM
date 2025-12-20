package pl.edu.agh.dp.entities;

import lombok.Getter;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.*;
import pl.edu.agh.dp.core.mapping.InheritanceType;

@Getter
@Setter
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public class User {
    @Id(autoIncrement = false)
    private Long id;
    private String name;
    private String email;
    @Column(nullable = true, columnName = "haha")
    private String secondName;
    @Column(nullable = true)
    private String secondEmail;
}
