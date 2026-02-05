package entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.DiscriminatorValue;
import pl.edu.agh.dp.api.annotations.Entity;

import java.time.LocalDate;

/**
 * Raport - dziedziczy z Document (SINGLE_TABLE).
 */
@Getter
@Setter
@NoArgsConstructor
@DiscriminatorValue("REPORT")
//@Entity
public class Report extends Document {

    private String reportType;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private String status;
}
