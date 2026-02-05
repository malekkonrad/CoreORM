package pl.edu.agh.dp.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.Column;
import pl.edu.agh.dp.api.annotations.Entity;
import pl.edu.agh.dp.api.annotations.Id;
import pl.edu.agh.dp.api.annotations.Inheritance;
import pl.edu.agh.dp.core.mapping.InheritanceType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Konto - klasa bazowa dla hierarchii dziedziczenia JOINED.
 * Account <- BankAccount, SavingsAccount, InvestmentAccount
 * 
 * Demonstruje strategię JOINED:
 * - Każda klasa ma osobną tabelę
 * - Tabela bazowa zawiera wspólne pola
 * - Tabele pochodne zawierają tylko specyficzne pola + FK do tabeli bazowej
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class Account {

    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false, unique = true)
    private String accountNumber;

    @Column(nullable = false)
    private String accountName;

    private BigDecimal balance;

    private LocalDate openDate;

    private String currency;

    @Column(defaultValue = "true")
    private Boolean isActive;
}
