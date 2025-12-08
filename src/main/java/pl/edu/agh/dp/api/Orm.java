package pl.edu.agh.dp.api;

import pl.edu.agh.dp.core.session.ConfigurationImpl;

public final class Orm {
    private Orm() {}

    public static Configuration configure() {
        return new ConfigurationImpl();
    }
}
