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

package de.dytanic.cloudnet.driver.event.events.service;

import de.dytanic.cloudnet.driver.service.ServiceInfoSnapshot;
import de.dytanic.cloudnet.driver.service.ServiceLifeCycle;
import org.jetbrains.annotations.NotNull;

public final class CloudServiceLifecycleChangeEvent extends CloudServiceEvent {

  private final ServiceLifeCycle lastLifeCycle;

  public CloudServiceLifecycleChangeEvent(@NotNull ServiceLifeCycle lastLifeCycle, @NotNull ServiceInfoSnapshot info) {
    super(info);
    this.lastLifeCycle = lastLifeCycle;
  }

  public @NotNull ServiceLifeCycle lastLifeCycle() {
    return this.lastLifeCycle;
  }

  public @NotNull ServiceLifeCycle newLifeCycle() {
    return this.serviceInfo.lifeCycle();
  }
}
