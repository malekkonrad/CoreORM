package pl.edu.agh.dp.test;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import pl.edu.agh.dp.core.mapping.InheritanceType;
import pl.edu.agh.dp.core.mapping.annotations.*;

import java.util.List;

@Getter
@Setter
public class User {
    @Id()
    Long id;
    String name;

    @OneToMany()
    @JoinColumn(nullable = true)
    List<Transaction> transactions;

}
