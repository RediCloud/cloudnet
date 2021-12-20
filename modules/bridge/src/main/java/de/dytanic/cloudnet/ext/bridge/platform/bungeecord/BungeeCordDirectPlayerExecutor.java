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

package de.dytanic.cloudnet.ext.bridge.platform.bungeecord;

import static net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection;
import static net.md_5.bungee.api.chat.TextComponent.fromLegacyText;

import de.dytanic.cloudnet.common.collection.Pair;
import de.dytanic.cloudnet.ext.bridge.platform.PlatformBridgeManagement;
import de.dytanic.cloudnet.ext.bridge.platform.PlatformPlayerExecutorAdapter;
import de.dytanic.cloudnet.ext.bridge.player.executor.ServerSelectorType;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectEvent.Reason;
import org.jetbrains.annotations.Nullable;

final class BungeeCordDirectPlayerExecutor extends PlatformPlayerExecutorAdapter {

  private final UUID uniqueId;
  private final PlatformBridgeManagement<ProxiedPlayer, ?> management;
  private final Supplier<Collection<? extends ProxiedPlayer>> playerSupplier;

  public BungeeCordDirectPlayerExecutor(
    @NonNull UUID uniqueId,
    @NonNull PlatformBridgeManagement<ProxiedPlayer, ?> management,
    @NonNull Supplier<Collection<? extends ProxiedPlayer>> playerSupplier
  ) {
    this.uniqueId = uniqueId;
    this.management = management;
    this.playerSupplier = playerSupplier;
  }

  @Override
  public @NonNull UUID uniqueId() {
    return this.uniqueId;
  }

  @Override
  public void connect(@NonNull String serviceName) {
    ServerInfo serverInfo = ProxyServer.getInstance().getServerInfo(serviceName);
    if (serverInfo != null) {
      this.playerSupplier.get().forEach(player -> player.connect(serverInfo, Reason.PLUGIN));
    }
  }

  @Override
  public void connectSelecting(@NonNull ServerSelectorType selectorType) {
    this.management.cachedServices().stream()
      .sorted(selectorType.comparator())
      .map(service -> ProxyServer.getInstance().getServerInfo(service.name()))
      .filter(Objects::nonNull)
      .findFirst()
      .ifPresent(server -> this.playerSupplier.get().forEach(player -> player.connect(server, Reason.PLUGIN)));
  }

  @Override
  public void connectToFallback() {
    this.playerSupplier.get().stream()
      .map(player -> new Pair<>(player, this.management.fallback(player)))
      .filter(pair -> pair.second().isPresent())
      .map(p -> new Pair<>(p.first(), ProxyServer.getInstance().getServerInfo(p.second().get().name())))
      .filter(pair -> pair.second() != null)
      .forEach(pair -> pair.first().connect(pair.second(), Reason.PLUGIN));
  }

  @Override
  public void connectToGroup(@NonNull String group, @NonNull ServerSelectorType selectorType) {
    this.management.cachedServices().stream()
      .filter(service -> service.configuration().groups().contains(group))
      .sorted(selectorType.comparator())
      .map(service -> ProxyServer.getInstance().getServerInfo(service.name()))
      .filter(Objects::nonNull)
      .forEach(server -> this.playerSupplier.get().forEach(player -> player.connect(server, Reason.PLUGIN)));
  }

  @Override
  public void connectToTask(@NonNull String task, @NonNull ServerSelectorType selectorType) {
    this.management.cachedServices().stream()
      .filter(service -> service.serviceId().taskName().equals(task))
      .sorted(selectorType.comparator())
      .map(service -> ProxyServer.getInstance().getServerInfo(service.name()))
      .filter(Objects::nonNull)
      .forEach(server -> this.playerSupplier.get().forEach(player -> player.connect(server, Reason.PLUGIN)));
  }

  @Override
  public void kick(@NonNull Component message) {
    this.playerSupplier.get().forEach(player -> player.disconnect(fromLegacyText(legacySection().serialize(message))));
  }

  @Override
  protected void sendTitle(@NonNull Component title, @NonNull Component subtitle, int fadeIn, int stay, int fadeOut) {
    this.playerSupplier.get().forEach(player -> ProxyServer.getInstance().createTitle()
      .title(fromLegacyText(legacySection().serialize(title)))
      .subTitle(fromLegacyText(legacySection().serialize(subtitle)))
      .fadeIn(fadeIn)
      .stay(stay)
      .fadeOut(fadeOut)
      .send(player));
  }

  @Override
  public void sendMessage(@NonNull Component message) {
    this.playerSupplier.get().forEach(player -> player.sendMessage(fromLegacyText(legacySection().serialize(message))));
  }

  @Override
  public void sendChatMessage(@NonNull Component message, @Nullable String permission) {
    this.playerSupplier.get().forEach(player -> {
      if (permission == null || player.hasPermission(permission)) {
        player.sendMessage(fromLegacyText(legacySection().serialize(message)));
      }
    });
  }

  @Override
  public void sendPluginMessage(@NonNull String tag, byte[] data) {
    this.playerSupplier.get().forEach(player -> player.sendData(tag, data));
  }

  @Override
  public void dispatchProxyCommand(@NonNull String command) {
    this.playerSupplier.get().forEach(player -> player.chat(command));
  }
}