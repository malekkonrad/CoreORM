package pl.edu.agh.dp.core.finder;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@Getter
public class IsNull implements Condition {
    
    private final String field;
    private final boolean isNull;

    public IsNull(String field) {
        this.field = field;
        this.isNull = true;
    }
    
    @Override
    public String toSql(String tableAlias) {
        if (isNull) {
            return tableAlias + "." + field + " IS NULL";
        } else {
            return tableAlias + "." + field + " IS NOT NULL";
        }
    }
    
    @Override
    public List<Object> getParams() {
        return Collections.emptyList();
    }
}
