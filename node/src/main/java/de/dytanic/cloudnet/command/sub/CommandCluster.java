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

package de.dytanic.cloudnet.command.sub;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.CommandPermission;
import cloud.commandframework.annotations.Flag;
import cloud.commandframework.annotations.parsers.Parser;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import com.google.common.net.InetAddresses;
import de.dytanic.cloudnet.CloudNet;
import de.dytanic.cloudnet.cluster.IClusterNodeServer;
import de.dytanic.cloudnet.cluster.NodeServer;
import de.dytanic.cloudnet.command.annotation.CommandAlias;
import de.dytanic.cloudnet.command.annotation.Description;
import de.dytanic.cloudnet.command.exception.ArgumentNotAvailableException;
import de.dytanic.cloudnet.command.source.CommandSource;
import de.dytanic.cloudnet.common.column.ColumnFormatter;
import de.dytanic.cloudnet.common.column.RowBasedFormatter;
import de.dytanic.cloudnet.common.io.FileUtils;
import de.dytanic.cloudnet.common.language.I18n;
import de.dytanic.cloudnet.common.log.LogManager;
import de.dytanic.cloudnet.common.log.Logger;
import de.dytanic.cloudnet.common.unsafe.CPUUsageResolver;
import de.dytanic.cloudnet.driver.network.HostAndPort;
import de.dytanic.cloudnet.driver.network.chunk.TransferStatus;
import de.dytanic.cloudnet.driver.network.cluster.NetworkClusterNode;
import de.dytanic.cloudnet.driver.service.ServiceTemplate;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

@CommandAlias("clu")
@CommandPermission("cloudnet.command.cluster")
@Description("Manages the cluster and provides information about it")
public final class CommandCluster {

  private static final Logger LOGGER = LogManager.logger(CommandCluster.class);
  private static final DateFormat DEFAULT_FORMAT = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
  private static final RowBasedFormatter<IClusterNodeServer> FORMATTER = RowBasedFormatter.<IClusterNodeServer>builder()
    .defaultFormatter(ColumnFormatter.builder().columnTitles("Name", "State", "Listeners").build())
    .column(server -> server.getNodeInfo().uniqueId())
    .column(server -> {
      // we can display much more information if the node is connected
      if (server.isConnected()) {
        if (server.isHeadNode() && server.isDrain()) {
          return "Connected (Head, Draining)";
        } else if (server.isHeadNode()) {
          return "Connected (Head)";
        } else if (server.isDrain()) {
          return "Connected (Draining)";
        } else {
          return "Connected";
        }
      } else {
        return "Not connected";
      }
    })
    .column(server -> Arrays.stream(server.getNodeInfo().listeners())
      .map(HostAndPort::toString)
      .collect(Collectors.joining(", ")))
    .build();

  @Parser(suggestions = "clusterNodeServer")
  public IClusterNodeServer defaultClusterNodeServerParser(CommandContext<CommandSource> $, Queue<String> input) {
    var nodeId = input.remove();
    var nodeServer = CloudNet.getInstance().getClusterNodeServerProvider().getNodeServer(nodeId);
    if (nodeServer == null) {
      throw new ArgumentNotAvailableException(I18n.trans("command-cluster-node-not-found"));
    }

    return nodeServer;
  }

  @Suggestions("clusterNodeServer")
  public List<String> suggestClusterNodeServer(CommandContext<CommandSource> $, String input) {
    return CloudNet.getInstance().getClusterNodeServerProvider().getNodeServers()
      .stream()
      .map(clusterNodeServer -> clusterNodeServer.getNodeInfo().uniqueId())
      .collect(Collectors.toList());
  }

  @Parser(suggestions = "selfNodeServer")
  public NodeServer selfNodeServerParser(CommandContext<CommandSource> $, Queue<String> input) {
    var nodeId = input.remove();
    var provider = CloudNet.getInstance().getClusterNodeServerProvider();
    var selfNode = provider.getSelfNode();
    // check if the user requested the one node
    if (selfNode.getNodeInfo().uniqueId().equals(nodeId)) {
      return selfNode;
    }
    NodeServer nodeServer = provider.getNodeServer(nodeId);
    // check if the nodeServer exists
    if (nodeServer == null) {
      throw new ArgumentNotAvailableException(I18n.trans("command-cluster-node-not-found"));
    }
    return nodeServer;
  }

  @Suggestions("selfNodeServer")
  public List<String> suggestNodeServer(CommandContext<CommandSource> $, String input) {
    var provider = CloudNet.getInstance().getClusterNodeServerProvider();
    var nodes = provider.getNodeServers()
      .stream()
      .map(clusterNodeServer -> clusterNodeServer.getNodeInfo().uniqueId())
      .collect(Collectors.toList());
    // add the own node to the suggestions
    nodes.add(provider.getSelfNode().getNodeInfo().uniqueId());
    return nodes;
  }

  @Parser(suggestions = "networkClusterNode")
  public NetworkClusterNode defaultNetworkClusterNodeParser(CommandContext<CommandSource> $, Queue<String> input) {
    var nodeId = input.remove();
    var clusterNode = CloudNet.getInstance().nodeInfoProvider().node(nodeId);
    if (clusterNode == null) {
      throw new ArgumentNotAvailableException(I18n.trans("command-cluster-node-not-found"));
    }

    return clusterNode;
  }

  @Suggestions("networkClusterNode")
  public List<String> suggestNetworkClusterNode(CommandContext<CommandSource> $, String input) {
    return CloudNet.getInstance().getConfig().getClusterConfig().nodes()
      .stream()
      .map(NetworkClusterNode::uniqueId)
      .collect(Collectors.toList());
  }

  @Parser
  public HostAndPort defaultHostAndPortParser(CommandContext<CommandSource> $, Queue<String> input) {
    var address = input.remove();
    try {
      // create an uri with the tpc protocol
      var uri = URI.create("tcp://" + address);

      var host = uri.getHost();
      // check if the host and port are valid
      if (host == null || uri.getPort() == -1) {
        throw new ArgumentNotAvailableException(I18n.trans("command-cluster-invalid-host"));
      }

      var inetAddress = InetAddresses.forUriString(host);
      return new HostAndPort(inetAddress.getHostAddress(), uri.getPort());
    } catch (IllegalArgumentException exception) {
      throw new ArgumentNotAvailableException(I18n.trans("command-cluster-invalid-host"));
    }
  }

  @Parser(name = "noNodeId", suggestions = "clusterNode")
  public String noClusterNodeParser(CommandContext<CommandSource> $, Queue<String> input) {
    var nodeId = input.remove();
    for (var node : CloudNet.getInstance().getConfig().getClusterConfig().nodes()) {
      if (node.uniqueId().equals(nodeId)) {
        throw new ArgumentNotAvailableException(I18n.trans("command-tasks-node-not-found"));
      }
    }

    return nodeId;
  }

  @Parser(name = "staticService", suggestions = "staticService")
  public String staticServiceParser(CommandContext<CommandSource> $, Queue<String> input) {
    var name = input.remove();
    var manager = CloudNet.getInstance().cloudServiceProvider();
    if (manager.serviceByName(name) != null) {
      throw new ArgumentNotAvailableException(I18n.trans("command-cluster-push-static-service-running"));
    }
    if (!Files.exists(manager.getPersistentServicesDirectoryPath().resolve(name))) {
      throw new ArgumentNotAvailableException(I18n.trans("command-cluster-push-static-service-not-found"));
    }
    return name;
  }

  @Suggestions("staticService")
  public List<String> suggestNotStartedStaticServices(CommandContext<CommandSource> $, String input) {
    return this.resolveAllStaticServices();
  }

  @CommandMethod("cluster|clu shutdown")
  public void shutdownCluster(CommandSource source) {
    for (var nodeServer : CloudNet.getInstance().getClusterNodeServerProvider().getNodeServers()) {
      nodeServer.shutdown();
    }
    CloudNet.getInstance().stop();
  }

  @CommandMethod("cluster|clu add <nodeId> <host>")
  public void addNodeToCluster(
    CommandSource source,
    @Argument(value = "nodeId", parserName = "noNodeId") String nodeId,
    @Argument("host") HostAndPort hostAndPort
  ) {
    var nodeConfig = CloudNet.getInstance().getConfig();
    var networkCluster = nodeConfig.getClusterConfig();
    // add the new node to the cluster config
    networkCluster.nodes().add(new NetworkClusterNode(nodeId, new HostAndPort[]{hostAndPort}));
    nodeConfig.setClusterConfig(networkCluster);
    // write the changes to the file
    nodeConfig.save();
    source.sendMessage(I18n.trans("command-cluster-create-node-success"));
  }

  @CommandMethod("cluster|clu remove <nodeId>")
  public void removeNodeFromCluster(CommandSource source, @Argument("nodeId") NetworkClusterNode node) {
    var nodeConfig = CloudNet.getInstance().getConfig();
    var cluster = nodeConfig.getClusterConfig();
    // try to remove the node from the cluster config
    if (cluster.nodes().remove(node)) {
      // update the cluster config in the node config
      nodeConfig.setClusterConfig(cluster);
      // write the node config
      nodeConfig.save();

      source.sendMessage(I18n.trans("command-cluster-remove-node-success"));
    }
  }

  @CommandMethod("cluster|clu nodes")
  public void listNodes(CommandSource source) {
    source.sendMessage(FORMATTER.format(CloudNet.getInstance().getClusterNodeServerProvider().getNodeServers()));
  }

  @CommandMethod("cluster|clu node <nodeId>")
  public void listNode(CommandSource source, @Argument("nodeId") IClusterNodeServer nodeServer) {
    this.displayNode(source, nodeServer);
  }

  @CommandMethod("cluster|clu node <nodeId> set drain <enabled>")
  public void drainNode(
    CommandSource source,
    @Argument(value = "nodeId") NodeServer nodeServer,
    @Argument("enabled") boolean enabled
  ) {
    nodeServer.setDrain(enabled);
    source.sendMessage(I18n.trans("command-cluster-node-set-drain")
      .replace("%value%", String.valueOf(enabled).replace("%node%", nodeServer.getNodeInfo().uniqueId())));
  }

  @CommandMethod("cluster|clu sync")
  public void sync(CommandSource source) {
    source.sendMessage(I18n.trans("command-cluster-start-sync"));
    // perform a cluster sync that takes care of tasks, groups and more
    CloudNet.getInstance().getClusterNodeServerProvider().syncClusterData();
  }

  @CommandMethod("cluster|clu push templates [template]")
  public void pushTemplates(CommandSource source, @Argument("template") ServiceTemplate template) {
    // check if we need to push all templates or just a specific one
    if (template == null) {
      var localStorage = CloudNet.getInstance().localTemplateStorage();
      // resolve and push all local templates
      for (var localTemplate : localStorage.templates()) {
        this.pushTemplate(source, localTemplate);
      }
    } else {
      // only push the specific template that was given
      this.pushTemplate(source, template);
    }
  }

  @CommandMethod("cluster|clu push staticServices [service]")
  public void pushStaticServices(
    CommandSource source,
    @Argument(value = "service", parserName = "staticService") String service,
    @Flag("overwrite") boolean overwrite
  ) {
    var staticServicePath = CloudNet.getInstance().cloudServiceProvider().getPersistentServicesDirectoryPath();
    // check if we need to push all static services or just a specific one
    if (service == null) {
      // resolve all existing static services, that are not running and push them
      for (var serviceName : this.resolveAllStaticServices()) {
        this.pushStaticService(source, staticServicePath.resolve(serviceName), serviceName);
      }
    } else {
      // only push the specific static service that was given
      this.pushStaticService(source, staticServicePath.resolve(service), service);
    }
  }

  private void pushStaticService(
    @NotNull CommandSource source,
    @NotNull Path servicePath,
    @NotNull String serviceName
  ) {
    // zip the whole directory into a stream
    var stream = FileUtils.zipToStream(servicePath);
    // notify the source about the deployment
    source.sendMessage(I18n.trans("command-cluster-push-static-service-starting"));
    // deploy the static service into the cluster
    CloudNet.getInstance().getClusterNodeServerProvider().deployStaticServiceToCluster(serviceName, stream, true)
      .onComplete(transferStatus -> {
        if (transferStatus == TransferStatus.FAILURE) {
          // the transfer failed
          source.sendMessage(I18n.trans("command-cluster-push-static-service-failed"));
        } else {
          // the transfer was successful
          source.sendMessage(I18n.trans("command-cluster-push-static-service-success"));
        }
      });
  }

  private void pushTemplate(@NotNull CommandSource source, @NotNull ServiceTemplate template) {
    var templateName = template.toString();
    try {
      source.sendMessage(
        I18n.trans("command-cluster-push-template-compress").replace("%template%", templateName));
      // compress the template and create an InputStream
      var inputStream = template.storage().zipTemplate();
      // check if the template really exists in the given storage
      if (inputStream != null) {
        // deploy the template into the cluster
        CloudNet.getInstance().getClusterNodeServerProvider().deployTemplateToCluster(template, inputStream, true)
          .onComplete(transferStatus -> {
            if (transferStatus == TransferStatus.FAILURE) {
              // the transfer failed
              source.sendMessage(
                I18n.trans("command-cluster-push-template-failed").replace("%template%", templateName));
            } else {
              // the transfer was successful
              source.sendMessage(
                I18n.trans("command-cluster-push-template-success").replace("%template%", templateName));
            }
          });
      } else {
        source.sendMessage(I18n.trans("command-template-not-found").replace("%template%", templateName));
      }
    } catch (IOException exception) {
      LOGGER.severe("An exception occurred while compressing template %s", exception, templateName);
    }
  }

  private @NotNull List<String> resolveAllStaticServices() {
    var manager = CloudNet.getInstance().cloudServiceProvider();
    try {
      // walk through the static service directory
      return Files.walk(manager.getPersistentServicesDirectoryPath(), 1)
        // remove to root path we started at
        .filter(path -> !path.equals(manager.getPersistentServicesDirectoryPath()))
        // remove all services that are started, as we can't push them
        .filter(path -> manager.getLocalCloudService(path.getFileName().toString()) == null)
        // map to all names of the different services
        .map(path -> path.getFileName().toString())
        .collect(Collectors.toList());
    } catch (IOException e) {
      // we can't find any static service
      return Collections.emptyList();
    }
  }

  private void displayNode(CommandSource source, IClusterNodeServer node) {
    List<String> list = new ArrayList<>(Arrays.asList(
      " ",
      "Id: " + node.getNodeInfo().uniqueId() + (node.isHeadNode() ? " (Head)" : ""),
      "State: " + (node.isConnected() ? "Connected" : "Not connected"),
      " ",
      "Address: "
    ));

    for (var hostAndPort : node.getNodeInfo().listeners()) {
      list.add("- " + hostAndPort.host() + ":" + hostAndPort.port());
    }

    if (node.getNodeInfoSnapshot() != null) {
      list.add(" ");
      list.add("* ClusterNodeInfoSnapshot from " + DEFAULT_FORMAT
        .format(node.getNodeInfoSnapshot().creationTime()));

      list.addAll(Arrays.asList(
        "CloudServices (" + node.getNodeInfoSnapshot().currentServicesCount() + ") memory usage (U/R/M): "
          + node.getNodeInfoSnapshot().usedMemory() + "/" + node.getNodeInfoSnapshot().reservedMemory()
          + "/" + node.getNodeInfoSnapshot().maxMemory() + " MB",
        " ",
        "CPU usage process: " + CPUUsageResolver.FORMAT
          .format(node.getNodeInfoSnapshot().processSnapshot().cpuUsage()) + "%",
        "CPU usage system: " + CPUUsageResolver.FORMAT
          .format(node.getNodeInfoSnapshot().processSnapshot().systemCpuUsage()) + "%",
        "Threads: " + node.getNodeInfoSnapshot().processSnapshot().threads().size(),
        "Heap usage: " + (node.getNodeInfoSnapshot().processSnapshot().heapUsageMemory() / (1024 * 1024)) + "/" +
          (node.getNodeInfoSnapshot().processSnapshot().maxHeapMemory() / (1024 * 1024)) + "MB",
        " "
      ));
    }
    source.sendMessage(list);
  }

}
