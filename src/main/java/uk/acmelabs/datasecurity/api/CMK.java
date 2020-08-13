/**
 * Copyright [2020] [Carl Pulley]
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
package uk.acmelabs.datasecurity.api;

import static java.util.Map.entry;
import java.util.Map;

import software.amazon.awssdk.services.kms.model.KeyMetadata;

public class CMK {

  private final String id;
  private final String arn;

  public CMK(final KeyMetadata key) {
    this.id = key.keyId();
    this.arn = key.arn();
  }

  public CMK(final String id, final String arn) {
    this.id = id;
    this.arn = arn;
  }

  public String getId() {
    return this.id;
  }

  public String getArn() {
    return arn;
  }

  public String toString() {
    return String.format("CMK(id=%s, arn=%s)", id, arn);
  }

  public Map<String, Object> toMap() {
    return Map.ofEntries(
      entry("id", id),
      entry("arn", arn)
    );
  }
}
