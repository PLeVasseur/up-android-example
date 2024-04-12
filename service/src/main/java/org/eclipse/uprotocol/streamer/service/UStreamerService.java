/*
 * Copyright (c) 2023 General Motors GTO LLC
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * SPDX-FileType: SOURCE
 * SPDX-FileCopyrightText: 2023 General Motors GTO LLC
 * SPDX-License-Identifier: Apache-2.0
 */
package org.eclipse.uprotocol.streamer.service;

import static org.eclipse.uprotocol.common.util.UStatusUtils.STATUS_OK;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.status;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.packToAny;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.unpack;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.protobuf.Empty;

import org.eclipse.uprotocol.UPClient;
import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.usubscription.v3.CreateTopicRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionResponse;
import org.eclipse.uprotocol.core.usubscription.v3.USubscription;
import org.eclipse.uprotocol.example.v1.Door;
import org.eclipse.uprotocol.example.v1.DoorCommand;
import org.eclipse.uprotocol.streamer.common.Example;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.transport.builder.UAttributesBuilder;
import org.eclipse.uprotocol.uri.factory.UResourceBuilder;
import org.eclipse.uprotocol.v1.UAttributes;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UEntity;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UPayload;
import org.eclipse.uprotocol.v1.UPriority;
import org.eclipse.uprotocol.v1.UResource;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@SuppressWarnings("SameParameterValue")
public class UStreamerService extends Service {
    private static final String TAG = Example.SERVICE.getName();
    private static final UUri SERVICE_URI = UUri.newBuilder()
            .setEntity(Example.SERVICE)
            .build();
    private static final UResource DOOR_FRONT_LEFT = UResource.newBuilder()
            .setName("doors")
            .setInstance("front_left")
            .setMessage("Doors")
            .build();
    private static final UResource DOOR_FRONT_RIGHT = UResource.newBuilder()
            .setName("doors")
            .setInstance("front_right")
            .setMessage("Doors")
            .build();
    private static final Map<String, UUri> sDoorTopics = new HashMap<>();
    static {
        List.of(DOOR_FRONT_LEFT, DOOR_FRONT_RIGHT)
                .forEach(resource -> sDoorTopics.put(
                        resource.getInstance(), UUri.newBuilder(SERVICE_URI)
                                .setResource(resource)
                                .build()));
    }

    private static final Map<String, UUri> sMethodUris = new HashMap<>();
    static {
        List.of(Example.METHOD_EXECUTE_DOOR_COMMAND)
                .forEach(method -> sMethodUris.put(
                        method, UUri.newBuilder(SERVICE_URI)
                                .setResource(UResourceBuilder.forRpcRequest(method))
                                .build()));
    }

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Map<UUri, Consumer<UMessage>> mMethodHandlers = new HashMap<>();
    private final UListener mRequestListener = this::handleRequestMessage;
    private UPClient mUPClient;
    private USubscription.Stub mUSubscriptionStub;

    protected static final UPayload PAYLOAD = packToAny(Empty.getDefaultInstance());

    protected static final UEntity SERVICE = UEntity.newBuilder()
            .setName("client.test")
            .setVersionMajor(1)
            .build();

    protected static final UResource RESOURCE_RUST = UResource.newBuilder()
            .setName("resource")
            .setInstance("main")
            .setMessage("Rust")
            .build();
    protected static final UUri RESOURCE_URI_TO_RUST = UUri.newBuilder()
            .setEntity(SERVICE)
            .setResource(RESOURCE_RUST)
            .build();
    private static final UMessage MESSAGE_TO_RUST = buildMessage(PAYLOAD, buildPublishAttributes(RESOURCE_URI_TO_RUST));

    protected static @NonNull UMessage buildMessage(UPayload payload, UAttributes attributes) {
        final UMessage.Builder builder = UMessage.newBuilder();
        if (payload != null) {
            builder.setPayload(payload);
        }
        if (attributes != null) {
            builder.setAttributes(attributes);
        }
        return builder.build();
    }

    protected static @NonNull UAttributes buildPublishAttributes(@NonNull UUri source) {
        return newPublishAttributesBuilder(source).build();
    }

    protected static @NonNull UAttributesBuilder newPublishAttributesBuilder(@NonNull UUri source) {
        return UAttributesBuilder.publish(source, UPriority.UPRIORITY_CS0);
    }

    private static @NonNull UUri mapDoorTopic(@NonNull String instance) {
        final UUri topic = sDoorTopics.get(instance);
        return (topic != null) ? topic : UUri.getDefaultInstance();
    }

    private static @NonNull UUri mapMethodUri(@NonNull String method) {
        final UUri uri = sMethodUris.get(method);
        return (uri != null) ? uri : UUri.getDefaultInstance();
    }

    private void subscribe(@NonNull UUri topic) {
        CompletableFuture<SubscriptionResponse> future = mUSubscriptionStub.subscribe(SubscriptionRequest.newBuilder()
                .setTopic(topic)
                .setSubscriber(SubscriberInfo.newBuilder().
                        setUri(UUri.newBuilder()
                                .setEntity(mUPClient.getEntity())
                                .build()))
                .build()).toCompletableFuture();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mUPClient = UPClient.create(getApplicationContext(), Example.SERVICE, mExecutor, (client, ready) -> {
            if (ready) {
                Log.i(TAG, join(Key.EVENT, "uPClient connected"));
            } else {
                Log.w(TAG, join(Key.EVENT, "uPClient unexpectedly disconnected"));
            }
        });
        mUSubscriptionStub = USubscription.newStub(mUPClient);

        mUPClient.connect()
                .thenCompose(status -> {
                    logStatus("connect", status);
                    if (isOk(status)) {
                        subscribe(RESOURCE_URI_TO_RUST);

                        Thread thread = new Thread(() -> {
                            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                            NativeBridge.initializeStreamer(mUPClient, mUSubscriptionStub, UUri.class,
                                    UStatus.class, UListenerNativeBridge.class,
                                    NativeBridge.class);
                        });
                        thread.start();
                    }
                    return isOk(status) ?
                            CompletableFuture.completedFuture(status) :
                            CompletableFuture.failedFuture(new UStatusException(status));
                });

        // TODO: Call into native bridge module which will hand off the mUPClient and mUSubscriptionStub
    }

    @Override
    public @Nullable IBinder onBind(@NonNull Intent intent) {
        return new Binder();
    }

    @Override
    public void onDestroy() {
        mExecutor.shutdown();

        CompletableFuture.allOf(
                        unregisterMethod(mapMethodUri(Example.METHOD_EXECUTE_DOOR_COMMAND)))
                .exceptionally(exception -> null)
                .thenCompose(it -> mUPClient.disconnect())
                .whenComplete((status, exception) -> logStatus("disconnect", status));
        super.onDestroy();
    }

    private CompletableFuture<UStatus> registerMethod(@NonNull UUri methodUri, @NonNull Consumer<UMessage> handler) {
        return CompletableFuture.supplyAsync(() -> {
            final UStatus status = mUPClient.registerListener(methodUri, mRequestListener);
            if (isOk(status)) {
                mMethodHandlers.put(methodUri, handler);
            }
            return logStatus("registerMethod", status, Key.URI, stringify(methodUri));
        });
    }

    private CompletableFuture<UStatus> unregisterMethod(@NonNull UUri methodUri) {
        return CompletableFuture.supplyAsync(() -> {
            final UStatus status = mUPClient.unregisterListener(methodUri, mRequestListener);
            mMethodHandlers.remove(methodUri);
            return logStatus("unregisterMethod", status, Key.URI, stringify(methodUri));
        });
    }

    private CompletableFuture<UStatus> createTopic(@NonNull UUri topic) {
        return mUSubscriptionStub.createTopic(CreateTopicRequest.newBuilder()
                        .setTopic(topic)
                        .build())
                .toCompletableFuture()
                .whenComplete((status, exception) -> {
                    if (exception != null) { // Communication failure
                        status = toStatus(exception);
                    }
                    logStatus("createTopic", status, Key.TOPIC, stringify(topic));
                });
    }

    private void publish(@NonNull UMessage message) {
        final UStatus status = mUPClient.send(message);
        logStatus("publish", status, Key.TOPIC, stringify(message.getAttributes().getSource()));
    }

    private void handleRequestMessage(@NonNull UMessage requestMessage) {
        final UUri methodUri = requestMessage.getAttributes().getSink();
        final Consumer<UMessage> handler = mMethodHandlers.get(methodUri);
        if (handler != null) {
            handler.accept(requestMessage);
        }
    }

    private void executeDoorCommand(@NonNull UMessage requestMessage) {
        UStatus status;
        try {
            final DoorCommand request = unpack(requestMessage.getPayload(), DoorCommand.class)
                    .orElseThrow(IllegalArgumentException::new);
            final String instance = request.getDoor().getInstance();
            final DoorCommand.Action action = request.getAction();
            Log.i(TAG, join(Key.REQUEST, "executeDoorCommand", "instance", instance, "action", action));
            checkArgument(sDoorTopics.containsKey(instance), "Unknown door: " + instance);
            final boolean locked = switch (action) {
                case LOCK -> true;
                case UNLOCK -> false;
                default -> throw new UStatusException(UCode.INVALID_ARGUMENT, "Unknown action: " + action);
            };
            // Pretend that all required CAN signals were sent successfully.
            // Simulate a received signal below.
            mExecutor.execute(() -> publish(UMessage.newBuilder()
                    .setPayload(packToAny(Door.newBuilder()
                            .setInstance(instance)
                            .setLocked(locked)
                            .build()))
                    .setAttributes(UAttributesBuilder.publish(mapDoorTopic(instance), UPriority.UPRIORITY_CS0).build())
                    .build()));
            status = STATUS_OK;
        } catch (Exception e) {
            status = toStatus(e);
        }
        logStatus("executeDoorCommand", status);

        mUPClient.send(UMessage.newBuilder()
                .setPayload(packToAny(status))
                .setAttributes(UAttributesBuilder.response(requestMessage.getAttributes()).build())
                .build());
    }

    private @NonNull UStatus logStatus(@NonNull String method, @NonNull UStatus status, Object... args) {
        Log.println(isOk(status) ? Log.INFO : Log.ERROR, TAG, status(method, status, args));
        return status;
    }
}
