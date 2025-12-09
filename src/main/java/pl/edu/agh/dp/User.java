package pl.edu.agh.dp;

import lombok.Getter;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.Entity;

@Getter
@Setter
@Entity
public class User {
    private String id;
    private String name;
}
