package org.eclipse.uprotocol.streamer.service;

import org.eclipse.uprotocol.UPClient;
import org.eclipse.uprotocol.core.usubscription.v3.USubscription;

public class NativeBridge {
    public static native String initializeStreamer(UPClient client, USubscription.Stub uSubscription);

    public static native String teardownStreamer();

    static {
        // This actually loads the shared object that we'll be creating.
        // The actual location of the .so or .dll may differ based on your
        // platform.
        System.loadLibrary("ustreamer_native_bridge");
    }
}
