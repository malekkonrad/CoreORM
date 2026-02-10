package pl.edu.agh.dp.core.finder;

import lombok.Getter;

@Getter
public class Sort {

    public enum Direction {
        ASC, DESC
    }
    
    private final String field;
    private final Direction direction;
    
    private Sort(String field, Direction direction) {
        this.field = field;
        this.direction = direction;
    }

    public static Sort asc(String field) {
        return new Sort(field, Direction.ASC);
    }

    public static Sort desc(String field) {
        return new Sort(field, Direction.DESC);
    }

    public String toSql(String tableAlias) {
        return tableAlias + "." + field + " " + direction.name();
    }
}
