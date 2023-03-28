/*
 * Copyright 2019-2023 CloudNetService team & contributors
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

package eu.cloudnetservice.modules.signs.platform;

import eu.cloudnetservice.driver.provider.CloudServiceProvider;
import eu.cloudnetservice.driver.registry.ServiceRegistry;
import eu.cloudnetservice.driver.service.ServiceEnvironmentType;
import eu.cloudnetservice.driver.service.ServiceInfoSnapshot;
import eu.cloudnetservice.modules.bridge.BridgeServiceHelper;
import eu.cloudnetservice.modules.bridge.player.PlayerManager;
import eu.cloudnetservice.modules.bridge.player.executor.ServerSelectorType;
import eu.cloudnetservice.modules.signs.Sign;
import eu.cloudnetservice.modules.signs.configuration.SignConfigurationEntry;
import eu.cloudnetservice.modules.signs.configuration.SignLayout;
import eu.cloudnetservice.modules.signs.util.LayoutUtil;
import eu.cloudnetservice.modules.signs.util.PriorityUtil;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

public abstract class PlatformSign<P, C> implements Comparable<PlatformSign<P, C>> {

  private static final PlayerManager PLAYER_MANAGER = ServiceRegistry.first(PlayerManager.class);
  private static final CloudServiceProvider SERVICE_PROVIDER = ServiceRegistry.first(CloudServiceProvider.class);

  protected final Sign base;
  protected final Function<String, C> lineMapper;
  protected volatile ServiceInfoSnapshot target;

  public PlatformSign(@NonNull Sign base, @NonNull Function<String, C> lineMapper) {
    this.base = base;
    this.lineMapper = lineMapper;
  }

  public @NonNull Sign base() {
    return this.base;
  }

  public @Nullable ServiceInfoSnapshot currentTarget() {
    return this.target;
  }

  public void currentTarget(@Nullable ServiceInfoSnapshot snapshot) {
    this.target = snapshot;
  }

  public void handleInteract(@NonNull UUID playerUniqueId, @NonNull P playerInstance) {
    // keep a local copy of the target as the view might change due to concurrent update calls
    AtomicReference<ServiceInfoSnapshot> target = new AtomicReference<>(this.target);
    if (target.get() == null) {
      return;
    }

    // get the current state from the service snapshot, ignore if the target is not yet ready to accept players
    var state = BridgeServiceHelper.guessStateFromServiceInfoSnapshot(target.get());
    if (state == BridgeServiceHelper.ServiceInfoState.STOPPED
      || state == BridgeServiceHelper.ServiceInfoState.STARTING) {
      return;
    }

    // get the target to connect the player to, null indicates that the event was cancelled
    target.set(this.callSignInteractEvent(playerInstance));
    if (target.get() == null) {
      return;
    }

    if (target.get().configuration().serviceId().environmentName().equals(ServiceEnvironmentType.MULTI_PAPER.name())
      && target.get().configuration().serviceId().taskServiceId() == 1) {

      String taskName = target.get().configuration().serviceId().taskName();

      SERVICE_PROVIDER.servicesByTaskAsync(taskName)
        .whenComplete((services, throwable) -> {
          if (throwable != null) {
            return;
          }
          ServerSelectorType selectorType = ServerSelectorType.LOWEST_PLAYERS;
          services.stream()
            .filter(serviceInfoSnapshot -> serviceInfoSnapshot.configuration().serviceId().taskServiceId() != 1)
            .min(selectorType.comparator())
            .ifPresent(target::set);
        });
    }

    PLAYER_MANAGER.playerExecutor(playerUniqueId).connect(target.get().name());
  }

  public int priority() {
    return this.priority(false);
  }

  public int priority(@Nullable SignConfigurationEntry entry) {
    // check if the service has a snapshot
    var target = this.currentTarget();
    // no target has the lowest priority
    return target == null ? 0 : PriorityUtil.priority(target, entry);
  }

  public int priority(boolean lowerFullToSearching) {
    // check if the service has a snapshot
    var target = this.currentTarget();
    // no target has the lowest priority
    return target == null ? 0 : PriorityUtil.priority(target, lowerFullToSearching);
  }

  protected void changeSignLines(@NonNull SignLayout layout, @NonNull BiConsumer<Integer, C> lineSetter) {
    LayoutUtil.updateSignLines(layout, this.base.targetGroup(), this.target, this.lineMapper, lineSetter);
  }

  @Override
  public int compareTo(@NonNull PlatformSign<P, C> sign) {
    return Integer.compare(this.priority(), sign.priority());
  }

  public abstract boolean exists();

  public abstract boolean needsUpdates();

  public abstract void updateSign(@NonNull SignLayout layout);

  public abstract @Nullable ServiceInfoSnapshot callSignInteractEvent(@NonNull P player);
}
