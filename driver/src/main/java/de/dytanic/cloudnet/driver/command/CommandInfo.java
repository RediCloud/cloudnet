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

package de.dytanic.cloudnet.driver.command;

import de.dytanic.cloudnet.common.INameable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * The commandInfo class allows to easily serialize the command information
 */
public record CommandInfo(
  @NotNull String name,
  @NotNull Collection<String> aliases,
  @NotNull String permission,
  @NotNull String description,
  @NotNull List<String> usage
) implements INameable {

  @Contract("_ -> new")
  public static @NotNull CommandInfo empty(@NotNull String name) {
    return new CommandInfo(name, Collections.emptyList(), "", "", Collections.emptyList());
  }

  public @NotNull String joinNameToAliases(@NotNull String separator) {
    var result = this.name;
    if (!this.aliases.isEmpty()) {
      result += separator + String.join(separator, this.aliases);
    }

    return result;
  }
}
