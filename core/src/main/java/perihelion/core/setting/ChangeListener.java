package perihelion.core.setting;

@FunctionalInterface
public interface ChangeListener<T> {
    void changed(Setting<T> setting, T previousValue, T currentValue);
}
