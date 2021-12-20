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

package eu.cloudnetservice.cloudnet.ext.signs.platform.bukkit.functionality;

import de.dytanic.cloudnet.driver.CloudNetDriver;
import de.dytanic.cloudnet.ext.bridge.player.IPlayerManager;
import eu.cloudnetservice.cloudnet.ext.signs.platform.PlatformSignManagement;
import eu.cloudnetservice.cloudnet.ext.signs.platform.bukkit.event.BukkitCloudSignInteractEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class SignInteractListener implements Listener {

  protected final PlatformSignManagement<org.bukkit.block.Sign> signManagement;

  public SignInteractListener(PlatformSignManagement<org.bukkit.block.Sign> signManagement) {
    this.signManagement = signManagement;
  }

  @EventHandler
  public void handle(PlayerInteractEvent event) {
    var entry = this.signManagement.applicableSignConfigurationEntry();
    if (entry != null
      && event.getAction() == Action.RIGHT_CLICK_BLOCK
      && event.getClickedBlock() != null
      && event.getClickedBlock().getState() instanceof org.bukkit.block.Sign state) {
      var sign = this.signManagement.signAt(state, entry.targetGroup());
      if (sign != null) {
        var canConnect = this.signManagement.canConnect(sign, event.getPlayer()::hasPermission);

        var interactEvent = new BukkitCloudSignInteractEvent(event.getPlayer(), sign, !canConnect);
        Bukkit.getPluginManager().callEvent(interactEvent);

        if (!interactEvent.isCancelled()) {
          interactEvent.target().ifPresent(service -> {
            this.signManagement.signsConfiguration().sendMessage("server-connecting-message",
              event.getPlayer()::sendMessage, m -> m.replace("%server%", service.name()));
            this.playerManager().playerExecutor(event.getPlayer().getUniqueId()).connect(service.name());
          });
        }
      }
    }
  }

  protected IPlayerManager playerManager() {
    return CloudNetDriver.instance().servicesRegistry().firstService(IPlayerManager.class);
  }
}