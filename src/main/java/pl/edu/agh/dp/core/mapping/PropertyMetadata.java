package pl.edu.agh.dp.core.mapping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@AllArgsConstructor
@Setter
public class PropertyMetadata {
    String name;                    // "firstName"
    String columnName;              // "first_name"
    Class<?> type;                  // String.class
    String sqlType;                 // VARCHAR(255)
    boolean isId = false;           // false
    boolean autoIncrement = false;  // false
    boolean isUnique = false;       // false
    boolean isNullable = false;     // false
    boolean isIndex = false;        // false
    Object defaultValue = null;     // default to 0

    public String toString(){
        StringBuffer sb = new StringBuffer();
        sb.append("Field name=").append(name).append(", ");
        sb.append("Column name=").append(columnName).append(", ");
        sb.append("Column type=").append(type).append(", ");
        sb.append("Sql type=").append(sqlType).append(", ");
        sb.append("primary key=").append(isId).append(", ");
        sb.append("auto increment=").append(autoIncrement).append(", ");
        sb.append("is unique=").append(isUnique).append(", ");
        sb.append("is nullable=").append(isNullable).append(", ");
        sb.append("is index=").append(isIndex).append(", ");
        sb.append("default value=").append(defaultValue);
        return sb.toString();
    }
}
