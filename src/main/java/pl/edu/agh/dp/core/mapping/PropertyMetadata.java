package pl.edu.agh.dp.core.mapping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Getter
@AllArgsConstructor
@Setter
@NoArgsConstructor
public class PropertyMetadata implements Cloneable {
    private String name;                    // "firstName"
    private String columnName;              // "first_name"
    private Class<?> type;                  // String.class
    private String sqlType;                 // VARCHAR(255)
    boolean isId = false;           // false
    boolean autoIncrement = true;   // false
    boolean isUnique = false;       // false
    boolean isNullable = false;     // false
    boolean isIndex = false;        // false
    Object defaultValue = "__UNSET__";     // default to unset
    String references = null;       // if foreign key

    public PropertyMetadata(PropertyMetadata other) {
        this.name = other.getName();
        this.columnName = other.getColumnName();
        this.type = other.getType();
        this.sqlType = other.getSqlType();
        this.isId = other.isId();
        this.autoIncrement = other.isAutoIncrement();
        this.isUnique = other.isUnique();
        this.isNullable = other.isNullable();
        this.isIndex = other.isIndex();
        this.defaultValue = other.getDefaultValue();
        this.references = other.getReferences();
    }

    public String toSqlColumn() {
        StringBuffer sb = new StringBuffer();
        if (columnName.contains(", ")) {
            for (String sepName : columnName.split(", ")) {
                sb.append(" ").append(sepName);
                sb.append(" ").append(sqlType);
                sb.append(",\n");
            }
            sb.deleteCharAt(sb.length() - 1);
            sb.deleteCharAt(sb.length() - 1);
            return sb.toString();
        }
        sb.append(" ").append(columnName);
        sb.append(" ").append(sqlType);
        if (defaultValue != "__UNSET__") {
            String value;
            if (defaultValue == "") {  // default to null (unnecessary in postgres)
                value = "NULL";
            } else if (type == String.class || type == LocalTime.class || type == LocalDate.class || type == UUID.class) { // string and some dates must be in quotes
                value = "'" + defaultValue + "'";
            } else if (type == LocalDateTime.class) {
                final DateTimeFormatter PG_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.nnnnnnnnn");
                value = "'" + ((LocalDateTime) defaultValue).format(PG_TIMESTAMP) + "'";
            } else if (type == OffsetDateTime.class) {
                DateTimeFormatter PG_TSTZ = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.nnnnnnnnnXXX");
                value = "'" + ((OffsetDateTime) defaultValue).format(PG_TSTZ) + "'";
            } else {  // numeric defaults
                value = defaultValue.toString();
            }
            sb.append(" DEFAULT ").append(value);
        }
        if (!isNullable) {
            sb.append(" NOT NULL");
        }
        if (isUnique) {
            sb.append(" UNIQUE");
        }
        return sb.toString();
    }

    public String toSqlConstraint(String tableName) {
        if (references != null) {
            return "CONSTRAINT " + tableName + "_" + columnName.replace(", ", "_") + "_constraint FOREIGN KEY (" + columnName + ")\n" +
                    "REFERENCES " + references;
        } else {
            return "";
        }
    }

    public String getReferencedName() {
        return references.substring(references.indexOf('(') + 1, references.indexOf(')'));
    }

    public String getReferencedTable() {
        return references.substring(0, references.indexOf('('));
    }

    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("[Field_name=").append(name).append(", ");
        sb.append("Column_name=").append(columnName).append(", ");
        sb.append("Column_type=").append(type).append(", ");
        sb.append("Sql_type=").append(sqlType).append(", ");
        sb.append("primary_key=").append(isId).append(", ");
        sb.append("auto_increment=").append(autoIncrement).append(", ");
        sb.append("is_unique=").append(isUnique).append(", ");
        sb.append("is_nullable=").append(isNullable).append(", ");
        sb.append("is_index=").append(isIndex).append(", ");
        sb.append("default_value=").append(defaultValue).append(", ");
        sb.append("references=").append(references).append("]");
        return sb.toString();
    }

    @Override
    public PropertyMetadata clone() {
        try {
            PropertyMetadata clone = (PropertyMetadata) super.clone();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
