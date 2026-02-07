package entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.core.mapping.annotations.DiscriminatorValue;

import java.time.LocalDate;

/**
 * Raport - dziedziczy z Document (SINGLE_TABLE).
 */
@Getter
@Setter
@NoArgsConstructor
@DiscriminatorValue("CV")
//@Entity
public class Cirriculum extends Document {

    private String name;
    private String surname;
    private LocalDate creationDate;
}
