package pl.edu.agh.dp.core.finder;

import java.util.List;

/**
 * Base interface for query conditions.
 * Each condition generates a SQL fragment with placeholders and provides parameter values.
 */
public interface Condition {

    String toSql(String tableAlias);
    List<Object> getParams();
    String getField();
}
