package pl.edu.agh.dp.core.mapping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@AllArgsConstructor
@Setter
@NoArgsConstructor
public class PropertyMetadata {
    private String name;                    // "firstName"
    private String columnName;              // "first_name"
    private Class<?> type;                  // String.class
    private String sqlType;                 // VARCHAR(255)
    @Setter
    boolean isId = false;           // false
    boolean autoIncrement = true;   // false
    boolean isUnique = false;       // false
    boolean isNullable = false;     // false
    boolean isIndex = false;        // false
    Object defaultValue = null;     // default to null
    String references = null;       // if foreign key

    public String toSqlColumn() {
        StringBuffer sb = new StringBuffer();
        sb.append("`").append(columnName);
        sb.append(" ").append(sqlType);
        if (isUnique) {
            sb.append(" UNIQUE");
        }
        if (isNullable) {
            sb.append(" NOT NULL");
        }
        if (isIndex) {
            sb.append(" INDEX");
        }
        if (defaultValue != "__UNSET__") {
            // TODO empty default value
            sb.append(" DEFAULT ").append(defaultValue);
        }
        return sb.toString();
    }

    public String toSqlConstraint() {
        if (references != null) {
            return "CONSTRAINT " + columnName + "_fkey FOREIGN KEY (" + columnName + ")\n" +
                    "REFERENCES " + references + " MATCH SIMPLE\n" +
                    "ON UPDATE NO ACTION ON DELETE NO ACTION";
        } else {
            return "";
        }
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("Field_name=").append(name).append(", ");
        sb.append("Column_name=").append(columnName).append(", ");
        sb.append("Column_type=").append(type).append(", ");
        sb.append("Sql_type=").append(sqlType).append(", ");
        sb.append("primary_key=").append(isId).append(", ");
        sb.append("auto_increment=").append(autoIncrement).append(", ");
        sb.append("is_unique=").append(isUnique).append(", ");
        sb.append("is_nullable=").append(isNullable).append(", ");
        sb.append("is_index=").append(isIndex).append(", ");
        sb.append("default_value=").append(defaultValue).append(", ");
        sb.append("references=").append(references);
        return sb.toString();
    }
}
