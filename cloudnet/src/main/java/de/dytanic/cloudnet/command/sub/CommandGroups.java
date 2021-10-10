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

import cloud.commandframework.annotations.parsers.Parser;
import de.dytanic.cloudnet.CloudNet;
import de.dytanic.cloudnet.command.exception.ArgumentNotAvailableException;
import de.dytanic.cloudnet.driver.service.GroupConfiguration;
import java.util.Queue;

public class CommandGroups {

  @Parser
  public GroupConfiguration defaultGroupParser(Queue<String> input) {
    String name = input.remove();

    GroupConfiguration configuration = CloudNet.getInstance().getGroupConfigurationProvider()
      .getGroupConfiguration(name);
    if (configuration == null) {
      throw new ArgumentNotAvailableException("Group not found");
    }

    return configuration;
  }

}