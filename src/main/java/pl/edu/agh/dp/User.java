package pl.edu.agh.dp;

import lombok.Getter;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.Column;
import pl.edu.agh.dp.api.annotations.Entity;
import pl.edu.agh.dp.api.annotations.Id;

import java.lang.reflect.Field;
import java.util.List;

@Getter
@Setter
@Entity(table = "users")
public class User {
    @Id
    private Long id;
    private String name;
    private String email;
    @Column(nullable = true, columnName = "haha")
    private String secondName;
    @Column(nullable = true)
    private String secondEmail;
}
