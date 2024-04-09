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
package org.eclipse.uprotocol.example.client;

import static android.content.Context.BIND_AUTO_CREATE;

import static androidx.core.content.ContextCompat.getMainExecutor;

import static org.eclipse.uprotocol.common.util.UStatusUtils.buildStatus;
import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.status;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.example.client.MainActivity.ENTITY;
import static org.eclipse.uprotocol.transport.builder.UPayloadBuilder.unpack;

import static java.lang.System.currentTimeMillis;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.eclipse.uprotocol.UPClient;
import org.eclipse.uprotocol.common.UStatusException;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriberInfo;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionRequest;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionResponse;
import org.eclipse.uprotocol.core.usubscription.v3.SubscriptionStatus;
import org.eclipse.uprotocol.core.usubscription.v3.USubscription;
import org.eclipse.uprotocol.core.usubscription.v3.UnsubscribeRequest;
import org.eclipse.uprotocol.core.usubscription.v3.Update;
import org.eclipse.uprotocol.example.v1.Door;
import org.eclipse.uprotocol.example.v1.DoorCommand;
import org.eclipse.uprotocol.example.v1.Example;
import org.eclipse.uprotocol.transport.UListener;
import org.eclipse.uprotocol.v1.UCode;
import org.eclipse.uprotocol.v1.UMessage;
import org.eclipse.uprotocol.v1.UResource;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class USubscriptionFragment extends Fragment {
    private static final String TAG = ENTITY.getName();
    private static final UUri TOPIC_DOOR_FRONT_LEFT = UUri.newBuilder()
            .setEntity(Example.SERVICE)
            .setResource(Example.DOOR_FRONT_LEFT)
            .build();
    private static final UUri TOPIC_SUBSCRIPTION_UPDATE = UUri.newBuilder()
            .setEntity(USubscription.SERVICE)
            .setResource(UResource.newBuilder()
                    .setName("subscriptions")
                    .setMessage("Update")
                    .build())
            .build();
    private static final String EXAMPLE_SERVICE_PACKAGE = "org.eclipse.uprotocol.example.service";
    private static final ComponentName EXAMPLE_SERVICE_COMPONENT =
            new ComponentName(EXAMPLE_SERVICE_PACKAGE, EXAMPLE_SERVICE_PACKAGE + ".ExampleService");

    private final ServiceConnection mServiceConnectionListener = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mLog.i(TAG, join(Key.EVENT, "Service started", Key.PACKAGE, name.getPackageName()));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mLog.i(TAG, join(Key.EVENT, "Service stopped", Key.PACKAGE, name.getPackageName()));
        }
    };
    private final UListener mUListener = this::handleMessage;
    private final Logger mLog = new Logger();

    private UPClient mUPClient;
    private USubscription.Stub mUSubscriptionStub;
    private Example.Stub mExampleStub;
    private TextView mDoorLockState;
    private Button mDoorLockButton;
    private Button mDoorUnlockButton;

    public static @NonNull USubscriptionFragment newInstance() {
        return new USubscriptionFragment();
    }

    private void startService() {
        try {
            final Intent intent = new Intent().setComponent(EXAMPLE_SERVICE_COMPONENT);
            requireContext().bindService(intent, mServiceConnectionListener, BIND_AUTO_CREATE);
        } catch (Exception e) {
            logStatus("bindService", toStatus(e), Key.PACKAGE, EXAMPLE_SERVICE_PACKAGE);
        }
    }

    private void stopService() {
        requireContext().unbindService(mServiceConnectionListener);
    }

    @Override
    public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.usubcription_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        startService();
        // We use main thread executor for simplicity of demonstration API, consider to use own
        // working thread to handle events.
        final Context context = requireContext();
        mUPClient = UPClient.create(context, getMainExecutor(context), (client, ready) -> {
            if (ready) {
                mLog.i(TAG, join(Key.EVENT, "uPClient connected"));
            } else {
                mLog.w(TAG, join(Key.EVENT, "uPClient unexpectedly disconnected"));
            }
        });
        mUSubscriptionStub = USubscription.newStub(mUPClient);
        mExampleStub = Example.newStub(mUPClient);
        mUPClient.connect()
                .thenCompose(status -> {
                    logStatus("connect", status);
                    return isOk(status) ?
                            CompletableFuture.completedFuture(status) :
                            CompletableFuture.failedFuture(new UStatusException(status));
                })
                .thenCompose(it -> CompletableFuture.allOf(
                        registerListener(TOPIC_SUBSCRIPTION_UPDATE),
                        subscribe(TOPIC_DOOR_FRONT_LEFT)));

        final View layout = requireView();
        mLog.setOutput(layout.findViewById(R.id.output), layout.findViewById(R.id.output_scroller));
        layout.findViewById(R.id.clear_output_button).setOnClickListener(bview -> mLog.clear());
        mDoorLockState = layout.findViewById(R.id.door_lock_state);
        mDoorLockButton = layout.findViewById(R.id.door_lock_button);
        mDoorLockButton.setOnClickListener(dview -> setDoorLocked(Example.DOOR_FRONT_LEFT.getInstance(), true));
        mDoorUnlockButton = layout.findViewById(R.id.door_unlock_button);
        mDoorUnlockButton.setOnClickListener(doneview -> setDoorLocked(Example.DOOR_FRONT_LEFT.getInstance(), false));
        updateDoorState(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mLog.reset();
        stopService();
        CompletableFuture.allOf(
                        unregisterListener(TOPIC_SUBSCRIPTION_UPDATE),
                        unsubscribe(TOPIC_DOOR_FRONT_LEFT))
                .exceptionally(exception -> null)
                .thenCompose(it -> mUPClient.disconnect())
                .whenComplete((status, exception) -> logStatus("disconnect", status));
    }

    private @NonNull CompletableFuture<UStatus> registerListener(@NonNull UUri topic) {
        return CompletableFuture.supplyAsync(() -> {
            final UStatus status = mUPClient.registerListener(topic, mUListener);
            return logStatus("registerListener", status, Key.TOPIC, stringify(topic));
        });
    }

    private @NonNull CompletableFuture<UStatus> unregisterListener(@NonNull UUri topic) {
        return CompletableFuture.supplyAsync(() -> {
            final UStatus status = mUPClient.unregisterListener(topic, mUListener);
            return logStatus("unregisterListener", status, Key.TOPIC, stringify(topic));
        });
    }

    @SuppressWarnings("SameParameterValue")
    private CompletableFuture<UCode> subscribe(@NonNull UUri topic) {
        final CompletionStage<SubscriptionResponse> subscribeStage = mUSubscriptionStub.subscribe(
                        SubscriptionRequest.newBuilder()
                                .setTopic(topic)
                                .setSubscriber(SubscriberInfo.newBuilder()
                                        .setUri(mUPClient.getUri())
                                        .build())
                                .build())
                .whenComplete((response, exception) -> {
                    if (exception != null) { // Communication failure
                        final UStatus status = toStatus(exception);
                        logStatus(USubscription.METHOD_SUBSCRIBE, status, Key.TOPIC, stringify(topic));
                    }
                });
        return registerListener(topic)
                .thenCombine(subscribeStage, (registerStatus, subscriptionResponse) -> {
                    if (!isOk(registerStatus)) {
                        return registerStatus.getCode();
                    }
                    final SubscriptionStatus status = subscriptionResponse.getStatus();
                    logStatus(USubscription.METHOD_SUBSCRIBE, buildStatus(status.getCode(), status.getMessage()),
                            Key.TOPIC, stringify(topic), Key.STATE, status.getState());
                    return status.getCode();
                });
    }

    @SuppressWarnings("SameParameterValue")
    private CompletableFuture<UStatus> unsubscribe(@NonNull UUri topic) {
        final CompletionStage<UStatus> unsubscribeStage = mUSubscriptionStub.unsubscribe(
                        UnsubscribeRequest.newBuilder()
                                .setTopic(topic)
                                .setSubscriber(SubscriberInfo.newBuilder()
                                        .setUri(mUPClient.getUri())
                                        .build())
                                .build())
                .whenComplete((status, exception) -> {
                    if (exception != null) { // Communication failure
                        status = toStatus(exception);
                        logStatus(USubscription.METHOD_UNSUBSCRIBE, status, Key.TOPIC, stringify(topic));
                    }
                });
        return unregisterListener(topic)
                .thenCombine(unsubscribeStage, (unregisterStatus, unsubscribeStatus) -> {
                    if (!isOk(unregisterStatus)) {
                        return unregisterStatus;
                    }
                    return logStatus(USubscription.METHOD_UNSUBSCRIBE, unsubscribeStatus, Key.TOPIC, stringify(topic));
                });
    }

    private void handleMessage(@NonNull UMessage message) {
        final UUri source = message.getAttributes().getSource();
        if (TOPIC_DOOR_FRONT_LEFT.equals(source)) {
            unpack(message.getPayload(), Door.class).ifPresent(this::updateDoorState);
        } else if (TOPIC_SUBSCRIPTION_UPDATE.equals(source)) {
            unpack(message.getPayload(), Update.class).ifPresent(update ->
                    mLog.i(TAG, join(Key.EVENT, "Subscription changed", Key.TOPIC, stringify(update.getTopic()),
                            Key.STATE, update.getStatus().getState())));
        }
    }

    private void updateDoorState(Door door) {
        if (door == null) {
            mDoorLockState.setText("");
        } else {
            mLog.i(TAG, join(Key.EVENT, "Door changed", Key.INSTANCE, door.getInstance(), "locked", door.getLocked()));
            mDoorLockState.setText(door.getLocked() ? R.string.locked : R.string.unlocked);
        }
    }

    private void setLockButtonsEnabled(boolean enabled) {
        mDoorLockButton.setEnabled(enabled);
        mDoorUnlockButton.setEnabled(enabled);
    }

    private void setDoorLocked(@NonNull String instance, boolean locked) {
        setLockButtonsEnabled(false);
        final DoorCommand.Action action = locked ? DoorCommand.Action.LOCK : DoorCommand.Action.UNLOCK;
        final DoorCommand request = DoorCommand.newBuilder()
                .setDoor(Door.newBuilder()
                        .setInstance(instance)
                        .build())
                .setAction(action)
                .build();
        mLog.i(TAG, join(Key.REQUEST, Example.METHOD_EXECUTE_DOOR_COMMAND, "instance", instance, "action", action));
        final long before = currentTimeMillis();
        mExampleStub.executeDoorCommand(request)
                .handle((status, exception) -> {
                    final long delta = currentTimeMillis() - before;
                    if (exception != null) {
                        status = toStatus(exception);
                    }
                    logStatus(Example.METHOD_EXECUTE_DOOR_COMMAND, status, Key.LATENCY, delta);
                    return null;
                })
                .thenRun(() -> getMainExecutor(requireContext()).execute(() -> setLockButtonsEnabled(true)));
    }

    private @NonNull UStatus logStatus(@NonNull String method, @NonNull UStatus status, Object... args) {
        mLog.println(isOk(status) ? Log.INFO : Log.ERROR, TAG, status(method, status, args));
        return status;
    }
}

