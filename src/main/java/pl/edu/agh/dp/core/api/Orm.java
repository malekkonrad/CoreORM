package pl.edu.agh.dp.core.api;

public final class Orm {
    private Orm() {}

    public static Configuration configure() {
        return new ConfigurationImpl();
    }
}
