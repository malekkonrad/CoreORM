package pl.edu.agh.dp.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.edu.agh.dp.api.annotations.Entity;

/**
 * Klient - dziedziczy z Person (JOINED).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
public class Client extends Person {
    private String companyName;
    private String taxId;
    private String address;
}
