package entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.core.mapping.annotations.Column;

import java.math.BigDecimal;

/**
 * Konto inwestycyjne - dziedziczy z Account (JOINED).
 * 
 * Tabela INVESTMENT_ACCOUNT zawiera tylko pola specyficzne dla konta inwestycyjnego
 * + klucz obcy do tabeli ACCOUNT.
 */
@Getter
@Setter
@NoArgsConstructor
//@Entity
public class InvestmentAccount extends Account {

    private String riskLevel; // low, medium, high

    private String portfolioType; // stocks, bonds, mixed, crypto

    private BigDecimal managementFee;

    @Column(defaultValue = "false")
    private Boolean hasBrokerAccess;

    private String investmentStrategy;

    private BigDecimal minimumInvestment;
}
