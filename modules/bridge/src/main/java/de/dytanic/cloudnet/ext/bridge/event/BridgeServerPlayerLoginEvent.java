/*
 * Copyright 2019-2021 CloudNetService team & contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.dytanic.cloudnet.ext.bridge.event;

import de.dytanic.cloudnet.driver.event.events.DriverEvent;
import de.dytanic.cloudnet.ext.bridge.player.CloudPlayer;
import de.dytanic.cloudnet.ext.bridge.player.NetworkServiceInfo;
import lombok.NonNull;

public final class BridgeServerPlayerLoginEvent extends DriverEvent {

  private final CloudPlayer cloudPlayer;
  private final NetworkServiceInfo serviceInfo;

  public BridgeServerPlayerLoginEvent(@NonNull CloudPlayer cloudPlayer, @NonNull NetworkServiceInfo serviceInfo) {
    this.cloudPlayer = cloudPlayer;
    this.serviceInfo = serviceInfo;
  }

  public @NonNull CloudPlayer cloudPlayer() {
    return this.cloudPlayer;
  }

  public @NonNull NetworkServiceInfo service() {
    return this.serviceInfo;
  }
}