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

import org.eclipse.uprotocol.v1.UAuthority;

public class TestConstants {
    public static final UAuthority AUTHORITY = UAuthority.newBuilder()
            .setName(String.join(".",  "domain", "device"))
            .build();

    public static final String JSON_NODE_EXAMPLE_SERVICE = """
            {
              "uri": "//device.domain/example.service",
              "nodes": [
                {
                  "uri": "//device.domain/example.service/1",
                  "nodes": [
                    {
                      "uri": "//device.domain/example.service/1/doors.front_left#Doors",
                      "properties": {
                        "id": {
                          "uInteger": 16
                        }
                      },
                      "type": "TOPIC"
                    }
                  ],
                  "properties": {
                    "id": {
                      "uInteger": 27
                    },
                    "major_version": {
                      "uInteger": 1
                    },
                    "minor_version": {
                      "uInteger": 0
                    }
                  },
                  "type": "VERSION"
                }
              ],
              "type": "ENTITY"
            }""";
}
