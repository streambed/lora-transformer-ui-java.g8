package $organization;format="package"$.$deviceType;format="camel"$.ui;

import com.github.huntc.streambed.ApplicationProcess;

/**
 * Bootstraps our application and handles signals
 */
public class $deviceType;format="Camel"$ServerEntryPoints {
    private static ApplicationProcess applicationProcess = new ApplicationProcess(new $deviceType;format="Camel"$Server());

    public static void main(String[] args) {
        applicationProcess.main(args);
    }

    public static void trap(int signal) {
        applicationProcess.trap(signal);
    }
}
