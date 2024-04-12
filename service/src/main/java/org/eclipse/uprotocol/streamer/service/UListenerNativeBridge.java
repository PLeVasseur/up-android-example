package org.eclipse.uprotocol.streamer.service;

import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UMessage;

public class UListenerNativeBridge implements UListener {

    private long listenerId;

    public UListenerNativeBridge(long listenerId) {
        this.listenerId = listenerId;
    }

    private native void onReceiveNative(long listenerId, byte[] messageBytes);

    @Override
    public void onReceive(UMessage message) {
        onReceiveNative(listenerId, message.toByteArray());
    }

    static {
        // This actually loads the shared object that we'll be creating.
        // The actual location of the .so or .dll may differ based on your
        // platform.
        System.loadLibrary("ustreamer_native_bridge");
    }
}
