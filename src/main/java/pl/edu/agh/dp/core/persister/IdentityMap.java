package pl.edu.agh.dp.core.persister;

import java.util.HashMap;
import java.util.Map;

public class IdentityMap {
    private final Map<Class<?>, Map<Object, Object>> map = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> clazz, Object id) {
        return (T) map.getOrDefault(clazz, Map.of()).get(id);
    }

    public void put(Class<?> clazz, Object id, Object entity) {
        map.computeIfAbsent(clazz, c -> new HashMap<>()).put(id, entity);
    }
}
