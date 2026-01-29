package pl.edu.agh.dp.core.api;

public interface Lazy {
    boolean isInitialized();
    void initialize();
}
