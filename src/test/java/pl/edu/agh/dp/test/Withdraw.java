package pl.edu.agh.dp.test;

import lombok.Getter;
import lombok.Setter;
import pl.edu.agh.dp.core.mapping.annotations.Entity;

@Getter
@Setter
public class Withdraw extends Transaction {
    Integer number;
}
