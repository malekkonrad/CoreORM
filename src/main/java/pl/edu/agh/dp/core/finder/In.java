package pl.edu.agh.dp.core.finder;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@AllArgsConstructor
@Getter
public class In implements Condition {
    
    private final String field;
    private final Collection<?> values;
    
    @Override
    public String toSql(String tableAlias) {
        if (values == null || values.isEmpty()) {
            // No values means false condition
            return "1 = 0";
        }
        String placeholders = String.join(", ", Collections.nCopies(values.size(), "?"));
        return tableAlias + "." + field + " IN (" + placeholders + ")";
    }
    
    @Override
    public List<Object> getParams() {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(values);
    }
}
