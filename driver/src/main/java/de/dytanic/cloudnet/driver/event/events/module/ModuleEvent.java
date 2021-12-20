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

package de.dytanic.cloudnet.driver.event.events.module;

import de.dytanic.cloudnet.driver.event.events.DriverEvent;
import de.dytanic.cloudnet.driver.module.IModuleProvider;
import de.dytanic.cloudnet.driver.module.IModuleProviderHandler;
import de.dytanic.cloudnet.driver.module.IModuleWrapper;
import lombok.NonNull;

/**
 * The {@link ModuleEvent}s are being called for every action in the {@link IModuleProviderHandler}.
 */
public abstract class ModuleEvent extends DriverEvent {

  private final IModuleWrapper module;
  private final IModuleProvider moduleProvider;

  public ModuleEvent(@NonNull IModuleProvider moduleProvider, @NonNull IModuleWrapper module) {
    this.moduleProvider = moduleProvider;
    this.module = module;
  }

  public @NonNull IModuleProvider moduleProvider() {
    return this.moduleProvider;
  }

  public @NonNull IModuleWrapper module() {
    return this.module;
  }
}