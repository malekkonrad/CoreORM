package entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.core.mapping.annotations.Column;
import pl.edu.agh.dp.core.mapping.annotations.DiscriminatorValue;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Faktura - dziedziczy z Document (SINGLE_TABLE).
 */
@Getter
@Setter
@NoArgsConstructor
@DiscriminatorValue("INVOICE")
//@Entity
public class Invoice extends Report {

    private String invoiceNumber;

    private LocalDate issueDate;

    private LocalDate dueDate;

    private BigDecimal totalAmount;

    private BigDecimal taxAmount;

    @Column(defaultValue = "PENDING")
    private String paymentStatus;
}
