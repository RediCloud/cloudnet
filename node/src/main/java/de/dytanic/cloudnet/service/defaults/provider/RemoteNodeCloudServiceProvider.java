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

package de.dytanic.cloudnet.service.defaults.provider;

import de.dytanic.cloudnet.common.concurrent.CompletedTask;
import de.dytanic.cloudnet.common.concurrent.ITask;
import de.dytanic.cloudnet.driver.network.INetworkChannel;
import de.dytanic.cloudnet.driver.network.rpc.RPCSender;
import de.dytanic.cloudnet.driver.provider.service.GeneralCloudServiceProvider;
import de.dytanic.cloudnet.driver.provider.service.RemoteSpecificCloudServiceProvider;
import de.dytanic.cloudnet.driver.service.ServiceInfoSnapshot;
import java.util.function.Supplier;
import lombok.NonNull;

public class RemoteNodeCloudServiceProvider extends RemoteSpecificCloudServiceProvider {

  private volatile ServiceInfoSnapshot snapshot;

  public RemoteNodeCloudServiceProvider(
    @NonNull GeneralCloudServiceProvider provider,
    @NonNull RPCSender providerSender,
    @NonNull Supplier<INetworkChannel> channelSupplier,
    @NonNull ServiceInfoSnapshot snapshot
  ) {
    super(provider, providerSender, channelSupplier, snapshot.serviceId().uniqueId());
    this.snapshot = snapshot;
  }

  @Override
  public @NonNull ServiceInfoSnapshot serviceInfo() {
    return this.snapshot;
  }

  @Override
  public @NonNull ITask<ServiceInfoSnapshot> serviceInfoAsync() {
    return CompletedTask.done(this.snapshot);
  }

  public void snapshot(@NonNull ServiceInfoSnapshot snapshot) {
    this.snapshot = snapshot;
  }
}