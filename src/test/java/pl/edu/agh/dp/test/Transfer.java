package pl.edu.agh.dp.test;


import lombok.Getter;
import lombok.Setter;
import pl.edu.agh.dp.core.mapping.annotations.Entity;

import java.math.BigDecimal;

@Getter
@Setter
public class Transfer extends Transaction {
    BigDecimal amount;
}
