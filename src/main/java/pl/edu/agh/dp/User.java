package pl.edu.agh.dp;

import lombok.Getter;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.Entity;

import java.lang.reflect.Field;
import java.util.List;

@Getter
@Setter
@Entity(table = "users")
public class User {
    private Long id;
    private String name;
    private String email;
    private String secondName;
    private String secondEmail;
}
