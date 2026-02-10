package pl.edu.agh.dp.core.finder;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@Getter
public class Eq implements Condition {
    
    private final String field;
    private final Object value;
    
    @Override
    public String toSql(String tableAlias) {
        if (value == null) {
            return tableAlias + "." + field + " IS NULL";
        }
        return tableAlias + "." + field + " = ?";
    }
    
    @Override
    public List<Object> getParams() {
        if (value == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(value);
    }
}
