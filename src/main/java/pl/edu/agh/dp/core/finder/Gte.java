package pl.edu.agh.dp.core.finder;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@Getter
public class Gte implements Condition {
    
    private final String field;
    private final Object value;
    
    @Override
    public String toSql(String tableAlias) {
        return tableAlias + "." + field + " >= ?";
    }
    
    @Override
    public List<Object> getParams() {
        return Collections.singletonList(value);
    }
}
