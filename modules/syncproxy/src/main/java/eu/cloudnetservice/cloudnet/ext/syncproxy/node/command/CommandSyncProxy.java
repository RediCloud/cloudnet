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

package eu.cloudnetservice.cloudnet.ext.syncproxy.node.command;

import cloud.commandframework.annotations.Argument;
import cloud.commandframework.annotations.CommandMethod;
import cloud.commandframework.annotations.parsers.Parser;
import cloud.commandframework.annotations.suggestions.Suggestions;
import cloud.commandframework.context.CommandContext;
import de.dytanic.cloudnet.CloudNet;
import de.dytanic.cloudnet.command.exception.ArgumentNotAvailableException;
import de.dytanic.cloudnet.command.source.CommandSource;
import de.dytanic.cloudnet.common.INameable;
import de.dytanic.cloudnet.common.language.I18n;
import de.dytanic.cloudnet.driver.service.GroupConfiguration;
import eu.cloudnetservice.cloudnet.ext.syncproxy.config.SyncProxyConfiguration;
import eu.cloudnetservice.cloudnet.ext.syncproxy.config.SyncProxyLoginConfiguration;
import eu.cloudnetservice.cloudnet.ext.syncproxy.config.SyncProxyMotd;
import eu.cloudnetservice.cloudnet.ext.syncproxy.config.SyncProxyTabList;
import eu.cloudnetservice.cloudnet.ext.syncproxy.config.SyncProxyTabListConfiguration;
import eu.cloudnetservice.cloudnet.ext.syncproxy.node.NodeSyncProxyManagement;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public final class CommandSyncProxy {

  private final NodeSyncProxyManagement syncProxyManagement;

  public CommandSyncProxy(@NotNull NodeSyncProxyManagement syncProxyManagement) {
    this.syncProxyManagement = syncProxyManagement;
  }

  @Parser(suggestions = "loginConfiguration")
  public SyncProxyLoginConfiguration loginConfigurationParser(CommandContext<CommandSource> $, Queue<String> input) {
    String name = input.remove();

    return this.syncProxyManagement.getConfiguration().getLoginConfigurations()
      .stream()
      .filter(login -> login.getTargetGroup().equals(name)).findFirst()
      .orElseThrow(
        () -> new ArgumentNotAvailableException(I18n.trans("module-syncproxy-command-create-entry-group-not-found")));
  }

  @Suggestions("loginConfiguration")
  public List<String> suggestLoginConfigurations(CommandContext<CommandSource> $, String input) {
    return this.syncProxyManagement.getConfiguration().getLoginConfigurations()
      .stream()
      .map(SyncProxyLoginConfiguration::getTargetGroup)
      .collect(Collectors.toList());
  }

  @Parser(name = "newConfiguration", suggestions = "newConfiguration")
  public String newConfigurationParser(CommandContext<CommandSource> $, Queue<String> input) {
    String name = input.remove();
    GroupConfiguration configuration = CloudNet.getInstance().getGroupConfigurationProvider()
      .getGroupConfiguration(name);
    if (configuration == null) {
      throw new ArgumentNotAvailableException(I18n.trans("command-service-base-group-not-found"));
    }

    if (this.syncProxyManagement.getConfiguration().getLoginConfigurations()
      .stream()
      .anyMatch(login -> login.getTargetGroup().equalsIgnoreCase(name))) {
      throw new ArgumentNotAvailableException(I18n.trans("module-syncproxy-command-create-entry-group-already-exists"));
    }

    if (this.syncProxyManagement.getConfiguration().getTabListConfigurations()
      .stream()
      .anyMatch(tabList -> tabList.getTargetGroup().equalsIgnoreCase(name))) {
      throw new ArgumentNotAvailableException(I18n.trans("module-syncproxy-command-create-entry-group-already-exists"));
    }
    return name;
  }

  @Suggestions("newConfiguration")
  public List<String> suggestNewLoginConfigurations(CommandContext<CommandSource> $, String input) {
    return CloudNet.getInstance().getGroupConfigurationProvider().getGroupConfigurations()
      .stream()
      .map(INameable::getName)
      .collect(Collectors.toList());
  }

  @CommandMethod("syncproxy|sp list")
  public void listConfigurations(CommandSource source) {
    this.displayListConfiguration(source, this.syncProxyManagement.getConfiguration());
  }

  @CommandMethod("syncproxy|sp create entry <targetGroup>")
  public void createEntry(
    CommandSource source,
    @Argument(value = "targetGroup", parserName = "newConfiguration") String name
  ) {
    SyncProxyLoginConfiguration loginConfiguration = SyncProxyLoginConfiguration.createDefault(name);
    SyncProxyTabListConfiguration tabListConfiguration = SyncProxyTabListConfiguration.createDefault(name);

    this.syncProxyManagement.getConfiguration().getLoginConfigurations().add(loginConfiguration);
    this.syncProxyManagement.getConfiguration().getTabListConfigurations().add(tabListConfiguration);
    this.updateSyncProxyConfiguration();

    source.sendMessage(I18n.trans("module-syncproxy-command-create-entry-success"));
  }

  @CommandMethod("syncproxy|sp target <targetGroup>")
  public void listConfiguration(
    CommandSource source,
    @Argument("targetGroup") SyncProxyLoginConfiguration loginConfiguration
  ) {
    this.displayConfiguration(source, loginConfiguration);
  }

  @CommandMethod("syncproxy|sp target <targetGroup> maxPlayers <amount>")
  public void setMaxPlayers(
    CommandSource source,
    @Argument("targetGroup") SyncProxyLoginConfiguration loginConfiguration,
    @Argument("amount") int amount
  ) {
    SyncProxyLoginConfiguration updatedLoginConfiguration = SyncProxyLoginConfiguration.builder(loginConfiguration)
      .maxPlayers(amount)
      .build();

    this.syncProxyManagement.setConfigurationSilently(
      SyncProxyConfiguration.builder(this.syncProxyManagement.getConfiguration())
        .addLoginConfiguration(updatedLoginConfiguration)
        .build());

    source.sendMessage(
      I18n.trans("module-syncproxy-command-set-maxplayers")
        .replace("%group%", loginConfiguration.getTargetGroup())
        .replace("%amount%", Integer.toString(amount)));
  }

  @CommandMethod("syncproxy|sp target <targetGroup> whitelist add <name>")
  public void addWhiteList(
    CommandSource source,
    @Argument("targetGroup") SyncProxyLoginConfiguration loginConfiguration,
    @Argument("name") String name
  ) {
    loginConfiguration.getWhitelist().add(name);
    this.updateSyncProxyConfiguration();

    source.sendMessage(I18n.trans("module-syncproxy-command-add-whitelist-entry")
      .replace("%name%", name)
      .replace("%group%", loginConfiguration.getTargetGroup()));
  }

  @CommandMethod("syncproxy|sp target <targetGroup> whitelist remove <name>")
  public void removeWhiteList(
    CommandSource source,
    @Argument("targetGroup") SyncProxyLoginConfiguration loginConfiguration,
    @Argument("name") String name
  ) {
    if (loginConfiguration.getWhitelist().remove(name)) {
      this.updateSyncProxyConfiguration();
    }

    source.sendMessage(I18n.trans("module-syncproxy-command-remove-whitelist-entry")
      .replace("%name%", name)
      .replace("%group%", loginConfiguration.getTargetGroup()));
  }

  @CommandMethod("syncproxy|sp target <targetGroup> maintenance <enabled>")
  public void maintenance(
    CommandSource source,
    @Argument("targetGroup") SyncProxyLoginConfiguration loginConfiguration,
    @Argument("enabled") boolean enabled
  ) {
    SyncProxyLoginConfiguration updatedLoginConfiguration = SyncProxyLoginConfiguration.builder(loginConfiguration)
      .maintenance(enabled)
      .build();

    this.syncProxyManagement.setConfigurationSilently(
      SyncProxyConfiguration.builder(this.syncProxyManagement.getConfiguration())
        .addLoginConfiguration(updatedLoginConfiguration)
        .build());

    source.sendMessage(I18n.trans("module-syncproxy-command-set-maintenance")
      .replace("%group%", loginConfiguration.getTargetGroup())
      .replace("%maintenance%", Boolean.toString(enabled)));
  }

  private void updateSyncProxyConfiguration() {
    this.syncProxyManagement.setConfiguration(this.syncProxyManagement.getConfiguration());
  }

  private void displayListConfiguration(CommandSource source, SyncProxyConfiguration syncProxyConfiguration) {
    for (SyncProxyLoginConfiguration syncProxyLoginConfiguration : syncProxyConfiguration
      .getLoginConfigurations()) {
      this.displayConfiguration(source, syncProxyLoginConfiguration);
    }

    for (SyncProxyTabListConfiguration syncProxyTabListConfiguration : syncProxyConfiguration
      .getTabListConfigurations()) {
      source.sendMessage(
        "* " + syncProxyTabListConfiguration.getTargetGroup(),
        "AnimationsPerSecond: " + syncProxyTabListConfiguration.getAnimationsPerSecond(),
        " ",
        "Entries: "
      );

      int index = 1;
      for (SyncProxyTabList tabList : syncProxyTabListConfiguration.getEntries()) {
        source.sendMessage(
          "- " + index++,
          "Header: " + tabList.getHeader(),
          "Footer: " + tabList.getFooter()
        );
      }
    }
  }

  private void displayConfiguration(CommandSource source,
    SyncProxyLoginConfiguration syncProxyLoginConfiguration) {
    source.sendMessage(
      "* " + syncProxyLoginConfiguration.getTargetGroup(),
      "Maintenance: " + (syncProxyLoginConfiguration.isMaintenance() ? "enabled" : "disabled"),
      "Max-Players: " + syncProxyLoginConfiguration.getMaxPlayers()
    );

    this.displayWhitelist(source, syncProxyLoginConfiguration.getWhitelist());

    source.sendMessage("Motds:");
    for (SyncProxyMotd syncProxyMotd : syncProxyLoginConfiguration.getMotds()) {
      this.displayMotd(source, syncProxyMotd);
    }

    for (SyncProxyMotd syncProxyMotd : syncProxyLoginConfiguration.getMaintenanceMotds()) {
      this.displayMotd(source, syncProxyMotd);
    }
  }

  private void displayMotd(CommandSource source, SyncProxyMotd syncProxyMotd) {
    source.sendMessage(
      "- Motd",
      "AutoSlot: " + syncProxyMotd.isAutoSlot(),
      "AutoSlot-MaxPlayerDistance: " + syncProxyMotd.getAutoSlotMaxPlayersDistance(),
      "Protocol-Text: " + syncProxyMotd.getProtocolText(),
      "First Line: " + syncProxyMotd.getFirstLine(),
      "Second Line: " + syncProxyMotd.getSecondLine(),
      "PlayerInfo: "
    );

    if (syncProxyMotd.getPlayerInfo() != null) {
      for (String playerInfoItem : syncProxyMotd.getPlayerInfo()) {
        source.sendMessage("- " + playerInfoItem);
      }
    }
  }

  private void displayWhitelist(CommandSource source, Collection<String> whitelistEntries) {
    source.sendMessage("Whitelist:");

    for (String whitelistEntry : whitelistEntries) {
      source.sendMessage("- " + whitelistEntry);
    }
  }
}
