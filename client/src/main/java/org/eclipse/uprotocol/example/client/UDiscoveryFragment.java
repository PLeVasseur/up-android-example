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

import static org.eclipse.uprotocol.common.util.UStatusUtils.checkArgument;
import static org.eclipse.uprotocol.common.util.UStatusUtils.checkStatusOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.isOk;
import static org.eclipse.uprotocol.common.util.UStatusUtils.toStatus;
import static org.eclipse.uprotocol.common.util.log.Formatter.join;
import static org.eclipse.uprotocol.common.util.log.Formatter.status;
import static org.eclipse.uprotocol.common.util.log.Formatter.stringify;
import static org.eclipse.uprotocol.example.client.MainActivity.ENTITY;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.protobuf.util.JsonFormat;

import org.eclipse.uprotocol.UPClient;
import org.eclipse.uprotocol.common.util.log.Formatter;
import org.eclipse.uprotocol.common.util.log.Key;
import org.eclipse.uprotocol.core.udiscovery.v3.AddNodesRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.DeleteNodesRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.FindNodesRequest;
import org.eclipse.uprotocol.core.udiscovery.v3.Node;
import org.eclipse.uprotocol.core.udiscovery.v3.UDiscovery;
import org.eclipse.uprotocol.example.v1.Example;
import org.eclipse.uprotocol.uri.serializer.LongUriSerializer;
import org.eclipse.uprotocol.v1.UStatus;
import org.eclipse.uprotocol.v1.UUri;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class UDiscoveryFragment extends Fragment {
    private static final String TAG = Formatter.tag(ENTITY.getName(), "Discovery");
    private static final String UDISCOVERY_PACKAGE = "org.eclipse.uprotocol.core.udiscovery";
    private static final ComponentName UDISCOVERY_COMPONENT =
            new ComponentName(UDISCOVERY_PACKAGE, UDISCOVERY_PACKAGE + ".UDiscoveryService");

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

    private final ScheduledExecutorService mExecutor = Executors.newScheduledThreadPool(1);
    private final Logger mLog = new Logger();

    private UDiscovery.Stub mUDiscoveryStub;
    private UPClient mUPClient;

    public static @NonNull UDiscoveryFragment newInstance() {
        return new UDiscoveryFragment();
    }

    private void startService() {
        try {
            final Intent intent = new Intent().setComponent(UDISCOVERY_COMPONENT);
            requireContext().bindService(intent, mServiceConnectionListener, BIND_AUTO_CREATE);
        } catch (Exception e) {
            logStatus("bindService", toStatus(e), Key.PACKAGE, UDISCOVERY_PACKAGE);
        }
    }

    private void stopService() {
        requireContext().unbindService(mServiceConnectionListener);
    }

    @Override
    public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.udiscovery_tab, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        startService();
        mUPClient = UPClient.create(requireContext(), mExecutor, (client, ready) -> {
            if (ready) {
                mLog.i(TAG, join(Key.EVENT, "uPClient connected"));
            } else {
                mLog.w(TAG, join(Key.EVENT, "uPClient unexpectedly disconnected"));
            }
        });
        mUDiscoveryStub = UDiscovery.newStub(mUPClient);
        mUPClient.connect().thenAccept(status -> logStatus("connect", status));

        final View layout = requireView();
        mLog.setOutput(layout.findViewById(R.id.output), layout.findViewById(R.id.output_scroller));
        layout.findViewById(R.id.clear_output_button)
                .setOnClickListener(v -> mLog.clear());
        final ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.udiscovery_methods, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        final Spinner spinner = layout.findViewById(R.id.udiscovery_spinner);
        spinner.setAdapter(adapter);
        layout.findViewById(R.id.execute)
                .setOnClickListener(v -> {
                    final String method = spinner.getSelectedItem().toString();
                    switch (method) {
                        case UDiscovery.METHOD_LOOKUP_URI -> lookupUri();
                        case UDiscovery.METHOD_FIND_NODES -> findNodes();
                        case UDiscovery.METHOD_ADD_NODES -> addNodes();
                        case UDiscovery.METHOD_DELETE_NODES -> deleteNodes();
                        case UDiscovery.METHOD_UPDATE_NODE,
                                UDiscovery.METHOD_FIND_NODE_PROPERTIES,
                                UDiscovery.METHOD_UPDATE_PROPERTY,
                                UDiscovery.METHOD_REGISTER_FOR_NOTIFICATIONS,
                                UDiscovery.METHOD_UNREGISTER_FOR_NOTIFICATIONS -> unimplemented();
                        default -> {}
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mLog.reset();
        stopService();
        mUPClient.disconnect().thenAccept(status -> logStatus("disconnect", status));
    }

    private void lookupUri() {
        final UUri uri = UUri.newBuilder()
                .setAuthority(TestConstants.AUTHORITY)
                .setEntity(Example.SERVICE)
                .setResource(Example.DOOR_FRONT_LEFT)
                .build();
        mLog.i(TAG, join(Key.METHOD, UDiscovery.METHOD_LOOKUP_URI, Key.URI, stringify(uri)));
        mUDiscoveryStub.lookupUri(uri)
                .thenAccept(uriResponse -> {
                    final UStatus status = uriResponse.getStatus();
                    checkStatusOk(status);
                    checkArgument(uriResponse.hasUris(), "Empty response");
                    logStatus(UDiscovery.METHOD_LOOKUP_URI, status, Key.URI, uriResponse.getUris().getUris(0));
                })
                .exceptionally(e -> {
                    logStatus(UDiscovery.METHOD_LOOKUP_URI, toStatus(e));
                    return null;
                });
    }

    private void findNodes() {
        final UUri uri = UUri.newBuilder()
                .setAuthority(TestConstants.AUTHORITY)
                .build();
        final FindNodesRequest request = FindNodesRequest.newBuilder()
                .setUri(LongUriSerializer.instance().serialize(uri))
                .setDepth(-1)
                .build();
        mLog.i(TAG, join(Key.METHOD, UDiscovery.METHOD_FIND_NODES, Key.URI, stringify(uri)));
        mUDiscoveryStub.findNodes(request)
                .thenAccept(findNodesResponse -> {
                    final UStatus status = findNodesResponse.getStatus();
                    checkStatusOk(status);
                    logStatus(UDiscovery.METHOD_FIND_NODES, status, Key.COUNT, findNodesResponse.getNodesCount());
                })
                .exceptionally(e -> {
                    logStatus(UDiscovery.METHOD_FIND_NODES, toStatus(e));
                    return null;
                });
    }

    private void addNodes() {
        final Node.Builder nodeBuilder = Node.newBuilder();
        try {
            JsonFormat.parser().ignoringUnknownFields().merge(TestConstants.JSON_NODE_EXAMPLE_SERVICE, nodeBuilder);
        } catch (Exception e) {
            logStatus(UDiscovery.METHOD_ADD_NODES, toStatus(e));
            return;
        }
        final UUri parentUri = UUri.newBuilder()
                .setAuthority(TestConstants.AUTHORITY)
                .build();
        final AddNodesRequest request = AddNodesRequest.newBuilder()
                .addNodes(nodeBuilder)
                .setParentUri(LongUriSerializer.instance().serialize(parentUri))
                .build();
        mLog.i(TAG, join(Key.METHOD, UDiscovery.METHOD_ADD_NODES, Key.URI,
                (request.getNodesCount() > 0) ? request.getNodes(0).getUri(): null));
        mUDiscoveryStub.addNodes(request)
                .thenAccept(status -> logStatus(UDiscovery.METHOD_ADD_NODES, status))
                .exceptionally(e -> {
                    logStatus(UDiscovery.METHOD_ADD_NODES, toStatus(e));
                    return null;
                });
    }

    private void deleteNodes() {
        final UUri uri = UUri.newBuilder()
                .setAuthority(TestConstants.AUTHORITY)
                .setEntity(Example.SERVICE)
                .setResource(Example.DOOR_FRONT_LEFT)
                .build();
        final DeleteNodesRequest request = DeleteNodesRequest.newBuilder()
                .addUris(LongUriSerializer.instance().serialize(uri))
                .build();
        mLog.i(TAG, join(Key.METHOD, UDiscovery.METHOD_DELETE_NODES, Key.URI, stringify(uri)));
        mUDiscoveryStub.deleteNodes(request)
                .thenAccept(status -> logStatus(UDiscovery.METHOD_DELETE_NODES, status))
                .exceptionally(e -> {
                    logStatus(UDiscovery.METHOD_ADD_NODES, toStatus(e));
                    return null;
                });
    }

    private void unimplemented() {
        mLog.w(TAG, join(Key.MESSAGE, "Method hasn't been implemented"));
    }

    @SuppressWarnings("UnusedReturnValue")
    private @NonNull UStatus logStatus(@NonNull String method, @NonNull UStatus status, Object... args) {
        mLog.println(isOk(status) ? Log.INFO : Log.ERROR, TAG, status(method, status, args));
        return status;
    }
}
