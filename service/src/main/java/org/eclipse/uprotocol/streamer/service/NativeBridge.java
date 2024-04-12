package org.eclipse.uprotocol.streamer.service;

import com.google.protobuf.InvalidProtocolBufferException;

import org.eclipse.uprotocol.UPClient;
import org.eclipse.uprotocol.core.usubscription.v3.USubscription;
import org.eclipse.uprotocol.v1.UUri;

public class NativeBridge {
    public static native String initializeStreamer(UPClient client, USubscription.Stub uSubscription);

    public static native String teardownStreamer();

    public static UUri deserializeToUUri(byte[] data) throws InvalidProtocolBufferException {
        return UUri.parseFrom(data);
    }

    static {
        // This actually loads the shared object that we'll be creating.
        // The actual location of the .so or .dll may differ based on your
        // platform.
        System.loadLibrary("ustreamer_native_bridge");
    }
}
