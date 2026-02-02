package pl.edu.agh.dp.demo.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.*;
import pl.edu.agh.dp.core.mapping.InheritanceType;

import java.time.LocalDate;

/**
 * Dokument - klasa bazowa dla hierarchii dziedziczenia SINGLE_TABLE.
 * Document <- Invoice, Report
 */
@Getter
@Setter
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorValue("DOCUMENT")
public class Document {
    @Id(autoIncrement = true)
    private Long id;

    @Column(nullable = false)
    private String title;

    private LocalDate createdDate;

    private String createdBy;

    private String content;
}
