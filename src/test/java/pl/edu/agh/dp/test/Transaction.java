package pl.edu.agh.dp.test;


import lombok.Getter;
import lombok.Setter;
import pl.edu.agh.dp.core.mapping.InheritanceType;
import pl.edu.agh.dp.core.mapping.annotations.*;

import java.time.LocalDate;

@Getter
@Setter
public class Transaction {
    @Id
    Long id;

    LocalDate date;

    @ManyToOne()
    @JoinColumn(nullable = true)
    User user;

}
