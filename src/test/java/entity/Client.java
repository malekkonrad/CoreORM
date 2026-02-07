package entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Klient - dziedziczy z Person (JOINED).
 */
@Getter
@Setter
@NoArgsConstructor
//@Entity
public class Client extends Person {
    private String companyName;
    private String taxId;
    private String address;
}
