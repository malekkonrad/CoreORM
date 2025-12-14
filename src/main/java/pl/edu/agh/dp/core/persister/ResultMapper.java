package pl.edu.agh.dp.core.persister;

import pl.edu.agh.dp.core.mapping.EntityMetadata;
import pl.edu.agh.dp.core.mapping.PropertyMetadata;

import java.lang.reflect.Field;
import java.sql.ResultSet;

public class ResultMapper {


    public static Object mapRow(EntityMetadata meta, ResultSet rs){
        try {
            Object obj = meta.getEntityClass().getDeclaredConstructor().newInstance();
            // result row first points to 0 so move it up
            if (!rs.next()) return null;

            for (PropertyMetadata pm : meta.getProperties()) {
                Field f = meta.getEntityClass().getDeclaredField(pm.getName());
                f.setAccessible(true);
                f.set(obj, rs.getObject(pm.getColumnName()));
            }


            return obj;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
