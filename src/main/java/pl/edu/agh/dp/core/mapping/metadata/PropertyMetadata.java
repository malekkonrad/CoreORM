package pl.edu.agh.dp.core.mapping.metadata;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
@Setter
public class PropertyMetadata {
    String name;       // np. "firstName"
    String columnName; // np. "first_name"
    Class<?> type;     // np. String.class
    boolean isId;
}
