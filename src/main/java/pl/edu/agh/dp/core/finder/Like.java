package pl.edu.agh.dp.core.finder;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * LIKE condition for string pattern matching: field LIKE pattern
 * Pattern can include SQL wildcards: % (any characters) and _ (single character)
 */
@AllArgsConstructor
@Getter
public class Like implements Condition {
    
    private final String field;
    private final String pattern;
    
    @Override
    public String toSql(String tableAlias) {
        return tableAlias + "." + field + " LIKE ?";
    }
    
    @Override
    public List<Object> getParams() {
        return Collections.singletonList(pattern);
    }
}
