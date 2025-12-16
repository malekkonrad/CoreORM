package pl.edu.agh.dp.entities;

import pl.edu.agh.dp.api.annotations.Entity;
import pl.edu.agh.dp.api.annotations.Id;
import pl.edu.agh.dp.api.annotations.Inheritance;
import pl.edu.agh.dp.core.mapping.InheritanceType;

@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public class Vehicle {
    @Id
    Long vehicleId;
    String vehicleName;
}
