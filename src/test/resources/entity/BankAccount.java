package pl.edu.agh.dp.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.Column;
import pl.edu.agh.dp.api.annotations.Entity;

/**
 * Konto bankowe - dziedziczy z Account (JOINED).
 * 
 * Tabela BANK_ACCOUNT zawiera tylko pola specyficzne dla konta bankowego
 * + klucz obcy do tabeli ACCOUNT.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class BankAccount extends Account {

    @Column(nullable = false)
    private String bankName;

    private String iban;

    private String swift;

    private String branchCode;

    @Column(defaultValue = "false")
    private Boolean hasDebitCard;

    @Column(defaultValue = "false")
    private Boolean hasOnlineBanking;
}
