package pl.edu.agh.dp.core.jdbc;

public interface Dialect {
    String getIdentitySelect(); // np. SELECT lastval()
    String getLimitClause(int limit, int offset);
    String quote(String identifier); // np. "user" albo `user`
}
