package pl.edu.agh.dp.core.mapping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TargetStatement {
    String stmt;
    String targetTableName;
    String rootTableName;
    @Getter
    public static final String targetName = "{{target}}";

    public TargetStatement(String stmt, String targetTableName) {
        this.stmt = stmt;
        this.targetTableName = targetTableName;
    }

    public String getStatement(String targetTableName) {
        return stmt.replace(targetName, targetTableName);
    }

    public String getStatement() {
        if (targetTableName == null) {
            if (stmt.isEmpty()) {
                return "";
            }
            throw new IllegalStateException("targetTableName not set");
        }
        return getStatement(targetTableName);
    }

    public boolean isBlank() {
        return stmt.isBlank();
    }

    @Override
    public String toString() {
        return stmt;
    }
}
