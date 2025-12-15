package pl.edu.agh.dp.entities;

import pl.edu.agh.dp.api.annotations.Entity;
import pl.edu.agh.dp.api.annotations.Id;

@Entity
public class Husky extends Dog {
    @Id
    Long huskyId;
    String how;
}
