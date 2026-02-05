package pl.edu.agh.dp.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.Column;
import pl.edu.agh.dp.api.annotations.Entity;

import java.math.BigDecimal;

/**
 * Konto oszczędnościowe - dziedziczy z Account (JOINED).
 * 
 * Tabela SAVINGS_ACCOUNT zawiera tylko pola specyficzne dla konta oszczędnościowego
 * + klucz obcy do tabeli ACCOUNT.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class SavingsAccount extends Account {

    private BigDecimal interestRate;

    private BigDecimal minimumBalance;

    private Integer withdrawalLimit; // max wypłat miesięcznie

    @Column(defaultValue = "false")
    private Boolean hasAutomaticTransfer;

    private String savingsGoal;
}
