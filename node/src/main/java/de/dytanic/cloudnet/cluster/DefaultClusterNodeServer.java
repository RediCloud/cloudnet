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

package de.dytanic.cloudnet.cluster;

import de.dytanic.cloudnet.CloudNet;
import de.dytanic.cloudnet.driver.channel.ChannelMessage;
import de.dytanic.cloudnet.driver.network.INetworkChannel;
import de.dytanic.cloudnet.driver.network.cluster.NetworkClusterNode;
import de.dytanic.cloudnet.driver.network.cluster.NetworkClusterNodeInfoSnapshot;
import de.dytanic.cloudnet.driver.network.def.NetworkConstants;
import de.dytanic.cloudnet.driver.network.protocol.IPacket;
import de.dytanic.cloudnet.driver.network.rpc.RPCSender;
import de.dytanic.cloudnet.driver.provider.NodeInfoProvider;
import de.dytanic.cloudnet.driver.provider.service.CloudServiceFactory;
import de.dytanic.cloudnet.driver.provider.service.RemoteCloudServiceFactory;
import de.dytanic.cloudnet.driver.provider.service.SpecificCloudServiceProvider;
import de.dytanic.cloudnet.driver.service.ServiceInfoSnapshot;
import java.util.Collection;
import java.util.Collections;
import lombok.NonNull;
import org.jetbrains.annotations.UnknownNullability;

public class DefaultClusterNodeServer extends DefaultNodeServer implements IClusterNodeServer {

  private final CloudNet cloudNet;
  private final RPCSender rpcSender;
  private final RPCSender nodeServerRPCSender;
  private final CloudServiceFactory cloudServiceFactory;
  private final DefaultClusterNodeServerProvider provider;

  private INetworkChannel channel;

  protected DefaultClusterNodeServer(
    @NonNull CloudNet cloudNet,
    @NonNull DefaultClusterNodeServerProvider provider,
    @NonNull NetworkClusterNode nodeInfo
  ) {
    this.cloudNet = cloudNet;
    this.provider = provider;

    this.rpcSender = cloudNet.rpcProviderFactory().providerForClass(
      cloudNet.networkClient(),
      NodeInfoProvider.class);
    this.nodeServerRPCSender = cloudNet.rpcProviderFactory().providerForClass(
      cloudNet.networkClient(),
      NodeServer.class);
    this.cloudServiceFactory = new RemoteCloudServiceFactory(
      this::channel,
      cloudNet.networkClient(),
      cloudNet.rpcProviderFactory());

    this.nodeInfo(nodeInfo);
  }

  @Override
  public boolean connected() {
    return this.channel != null;
  }

  @Override
  public void saveSendPacket(@NonNull IPacket packet) {
    if (this.channel != null) {
      this.channel.sendPacket(packet);
    }
  }

  @Override
  public void saveSendPacketSync(@NonNull IPacket packet) {
    if (this.channel != null) {
      this.channel.sendPacketSync(packet);
    }
  }

  @Override
  public boolean acceptableConnection(@NonNull INetworkChannel channel, @NonNull String nodeId) {
    return this.channel == null && this.nodeInfo.uniqueId().equals(nodeId);
  }

  @Override
  public void syncClusterData(boolean force) {
    var channelMessage = ChannelMessage.builder()
      .message("sync_cluster_data")
      .targetNode(this.nodeInfo.uniqueId())
      .channel(NetworkConstants.INTERNAL_MSG_CHANNEL)
      .buffer(this.cloudNet.dataSyncRegistry().prepareClusterData(force))
      .build();
    // if the data sync is forced there is no need to wait for a response
    if (force) {
      channelMessage.send();
    } else {
      // send and await a response
      var response = channelMessage.sendSingleQuery();
      if (response != null && response.content().readBoolean()) {
        // there was overridden data we need to handle
        this.cloudNet.dataSyncRegistry().handle(response.content(), true);
      }
    }
  }

  @Override
  public void shutdown() {
    ChannelMessage.builder()
      .message("cluster_node_shutdown")
      .targetNode(this.nodeInfo.uniqueId())
      .channel(NetworkConstants.INTERNAL_MSG_CHANNEL)
      .build()
      .send();
  }

  @Override
  public @NonNull Collection<String> sendCommandLine(@NonNull String commandLine) {
    if (this.channel != null) {
      return this.rpcSender.invokeMethod("sendCommandLine", commandLine).fireSync(this.channel);
    }

    return Collections.emptySet();
  }

  @Override
  public @NonNull CloudServiceFactory cloudServiceFactory() {
    return this.cloudServiceFactory;
  }

  @Override
  public @NonNull SpecificCloudServiceProvider cloudServiceProvider(@NonNull ServiceInfoSnapshot snapshot) {
    return this.cloudNet.cloudServiceProvider().specificProvider(snapshot.serviceId().uniqueId());
  }

  @Override
  public synchronized void close() throws Exception {
    if (this.channel != null) {
      this.channel.close();
      ClusterNodeServerUtils.handleNodeServerClose(this.channel, this);

      this.channel = null;
    }

    this.currentSnapshot = this.lastSnapshot = null;
    super.close();
  }

  @Override
  public @NonNull DefaultClusterNodeServerProvider provider() {
    return this.provider;
  }

  @Override
  public boolean drain() {
    return this.currentSnapshot.draining();
  }

  @Override
  public void drain(boolean drain) {
    if (this.channel != null) {
      this.nodeServerRPCSender.invokeMethod("drain", drain).fireSync(this.channel);
    }
  }

  @Override
  public @UnknownNullability INetworkChannel channel() {
    return this.channel;
  }

  @Override
  public void channel(@NonNull INetworkChannel channel) {
    this.channel = channel;
  }

  @Override
  public void nodeInfoSnapshot(@NonNull NetworkClusterNodeInfoSnapshot nodeInfoSnapshot) {
    if (this.currentSnapshot == null) {
      super.nodeInfoSnapshot(nodeInfoSnapshot);
      this.provider().refreshHeadNode();
    } else {
      super.nodeInfoSnapshot(nodeInfoSnapshot);
    }
  }
}