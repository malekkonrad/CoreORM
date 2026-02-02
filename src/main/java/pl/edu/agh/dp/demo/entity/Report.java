package pl.edu.agh.dp.demo.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.*;

import java.time.LocalDate;

/**
 * Raport - dziedziczy z Document (SINGLE_TABLE).
 */
@Getter
@Setter
@NoArgsConstructor
@DiscriminatorValue("REPORT")
public class Report extends Document {

    private String reportType;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private String status;
}
