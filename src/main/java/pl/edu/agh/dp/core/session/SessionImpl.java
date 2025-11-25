package pl.edu.agh.dp.core.session;

import pl.edu.agh.dp.api.Session;
import pl.edu.agh.dp.core.uow.DefaultUnitOfWork;
import pl.edu.agh.dp.core.uow.UnitOfWork;

public class SessionImpl implements Session {

    private final UnitOfWork unitOfWork;

    public SessionImpl() {
        this.unitOfWork = new DefaultUnitOfWork();
    }

    @Override
    public <T> void save(T entity) {

    }

    @Override
    public <T> T find(Class<T> entityClass, Object id) {
        return null;
    }

    @Override
    public <T> void delete(T entity) {

    }

    @Override
    public void close() {

    }
}
