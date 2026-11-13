package perihelion.core.diagnostics;

public final class Failures {
    private Failures() { }

    public static void rethrowFatal(Throwable failure) {
        if (failure instanceof VirtualMachineError) throw (VirtualMachineError) failure;
        if (failure instanceof ThreadDeath) throw (ThreadDeath) failure;
    }
}
