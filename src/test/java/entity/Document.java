package entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.core.mapping.annotations.DiscriminatorValue;
import pl.edu.agh.dp.core.mapping.annotations.Id;
import pl.edu.agh.dp.core.mapping.annotations.Inheritance;
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
//@Entity
public class Document {
    @Id(autoIncrement = true)
    private Long id;

//    @Column(nullable = true)
    private String title;

//    @Column(nullable = true)
    private LocalDate createdDate;

//    @Column(nullable = true)
    private String createdBy;

//    @Column(nullable = true)
    private String content;
}
