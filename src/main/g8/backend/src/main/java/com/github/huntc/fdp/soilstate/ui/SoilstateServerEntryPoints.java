package com.github.huntc.fdp.soilstate.ui;

import com.github.huntc.streambed.ApplicationProcess;

/**
 * Bootstraps our application and handles signals
 */
public class SoilstateServerEntryPoints {
    private static ApplicationProcess applicationProcess = new ApplicationProcess(new SoilstateServer());

    public static void main(String[] args) {
        applicationProcess.main(args);
    }

    public static void trap(int signal) {
        applicationProcess.trap(signal);
    }
}
