package $organization;format="package"$.$deviceType;format="camel"$.ui;

import com.cisco.streambed.ApplicationProcess;

/**
 * Bootstraps our application and handles signals
 */
public class $deviceType;format="Camel"$ServerEntryPoints {
    private static ApplicationProcess applicationProcess = null;

    public static void main(String[] args) {
        applicationProcess = new ApplicationProcess(new $deviceType;format="Camel"$Server());
        applicationProcess.main(args);
    }

    public static void trap(int signal) {
        if (applicationProcess != null) {
            applicationProcess.trap(signal);
        }
    }
}
