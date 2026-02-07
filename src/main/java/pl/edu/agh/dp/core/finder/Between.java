package pl.edu.agh.dp.core.finder;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;


@AllArgsConstructor
@Getter
public class Between implements Condition {
    
    private final String field;
    private final Object low;
    private final Object high;
    
    @Override
    public String toSql(String tableAlias) {
        return tableAlias + "." + field + " BETWEEN ? AND ?";
    }
    
    @Override
    public List<Object> getParams() {
        return Arrays.asList(low, high);
    }
}
