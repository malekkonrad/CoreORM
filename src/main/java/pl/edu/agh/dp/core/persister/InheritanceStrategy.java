package pl.edu.agh.dp.core.persister;

import javafx.util.Pair;
import pl.edu.agh.dp.api.Session;

import java.util.List;

public interface InheritanceStrategy {
    Pair<String, String> create();
    Object insert(Object entity, Session session);
    void update(Object entity, Session session);
    void delete(Object entity, Session session);
    Object findById(Object id, Session session);
    <T> List<T> findAll(Class<T> type, Session session, String joinStmt, String whereStmt);
}
