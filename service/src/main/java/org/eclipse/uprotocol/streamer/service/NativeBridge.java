package org.eclipse.uprotocol.streamer.service;

import com.google.protobuf.InvalidProtocolBufferException;

import org.eclipse.uprotocol.UPClient;
import org.eclipse.uprotocol.core.usubscription.v3.USubscription;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

public class NativeBridge {
    public static native String initializeStreamer(UPClient client, USubscription.Stub uSubscription,
                                                   Class<?> UUriClass, Class<?> UStatusClass,
                                                   Class<?> UListenerNativeBridgeClass,
                                                   Class<?> NativeBridgeClass, ClassLoader necessaryClassLoader);

    public static native String teardownStreamer();

    public static UUri deserializeToUUri(byte[] uuri) throws InvalidProtocolBufferException {
        return UUri.parseFrom(uuri);
    }

    public static UMessage deserializeToUMessage(byte[] message) throws InvalidProtocolBufferException {
        return UMessage.parseFrom(message);
    }

    public static byte[] serializeFromUStatus(UStatus data) {
        return data.toByteArray();
    }

    static {
        // This actually loads the shared object that we'll be creating.
        // The actual location of the .so or .dll may differ based on your
        // platform.
        System.loadLibrary("ustreamer_native_bridge");
    }
}
