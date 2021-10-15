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
import de.dytanic.cloudnet.driver.network.protocol.IPacket;
import de.dytanic.cloudnet.driver.network.rpc.RPCSender;
import de.dytanic.cloudnet.driver.provider.NodeInfoProvider;
import de.dytanic.cloudnet.driver.provider.service.CloudServiceFactory;
import de.dytanic.cloudnet.driver.provider.service.RemoteCloudServiceFactory;
import de.dytanic.cloudnet.driver.provider.service.SpecificCloudServiceProvider;
import de.dytanic.cloudnet.driver.service.ServiceInfoSnapshot;
import de.dytanic.cloudnet.network.packet.PacketServerChannelMessage;
import java.util.Collection;
import java.util.Collections;
import org.jetbrains.annotations.NotNull;

public class DefaultClusterNodeServer extends DefaultNodeServer implements IClusterNodeServer {

  private final CloudNet cloudNet;
  private final RPCSender rpcSender;
  private final CloudServiceFactory cloudServiceFactory;
  private final DefaultClusterNodeServerProvider provider;

  private INetworkChannel channel;

  protected DefaultClusterNodeServer(
    CloudNet cloudNet,
    DefaultClusterNodeServerProvider provider,
    NetworkClusterNode nodeInfo
  ) {
    this.cloudNet = cloudNet;
    this.provider = provider;

    this.rpcSender = cloudNet.getRPCProviderFactory().providerForClass(
      cloudNet.getNetworkClient(),
      NodeInfoProvider.class);
    this.cloudServiceFactory = new RemoteCloudServiceFactory(
      this::getChannel,
      cloudNet.getNetworkClient(),
      cloudNet.getRPCProviderFactory());

    this.setNodeInfo(nodeInfo);
  }

  @Override
  public void sendCustomChannelMessage(@NotNull ChannelMessage channelMessage) {
    this.saveSendPacket(new PacketServerChannelMessage(channelMessage));
  }

  @Override
  public boolean isConnected() {
    return this.channel != null;
  }

  @Override
  public void saveSendPacket(@NotNull IPacket packet) {
    if (this.channel != null) {
      this.channel.sendPacket(packet);
    }
  }

  @Override
  public void saveSendPacketSync(@NotNull IPacket packet) {
    if (this.channel != null) {
      this.channel.sendPacketSync(packet);
    }
  }

  @Override
  public boolean isAcceptableConnection(@NotNull INetworkChannel channel, @NotNull String nodeId) {
    return this.channel == null && this.nodeInfo.getUniqueId().equals(nodeId);
  }

  @Override
  public @NotNull Collection<String> sendCommandLine(@NotNull String commandLine) {
    if (this.channel != null) {
      return this.rpcSender.invokeMethod("sendCommandLine", commandLine).fireSync(this.channel);
    }

    return Collections.emptySet();
  }

  @NotNull
  @Override
  public CloudServiceFactory getCloudServiceFactory() {
    return this.cloudServiceFactory;
  }

  @Override
  public SpecificCloudServiceProvider getCloudServiceProvider(@NotNull ServiceInfoSnapshot snapshot) {
    return this.cloudNet.getCloudServiceProvider().getSpecificProvider(snapshot.getServiceId().getUniqueId());
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

  @NotNull
  @Override
  public DefaultClusterNodeServerProvider getProvider() {
    return this.provider;
  }

  @Override
  public INetworkChannel getChannel() {
    return this.channel;
  }

  @Override
  public void setChannel(@NotNull INetworkChannel channel) {
    this.channel = channel;
  }

  @Override
  public void setNodeInfoSnapshot(@NotNull NetworkClusterNodeInfoSnapshot nodeInfoSnapshot) {
    if (this.currentSnapshot == null) {
      super.setNodeInfoSnapshot(nodeInfoSnapshot);
      this.getProvider().refreshHeadNode();
    } else {
      super.setNodeInfoSnapshot(nodeInfoSnapshot);
    }
  }
}
