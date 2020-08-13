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

public class Message {

  private final byte[] data;
  private final byte[] dataKey;
  private final byte[] iv;
  private final CMK cmk;

  public Message(final byte[] data, final byte[] dataKey, final byte[] iv, final CMK cmk) {
    this.data = data;
    this.dataKey = dataKey;
    this.iv = iv;
    this.cmk = cmk;
  }

  public byte[] getData() {
    return this.data;
  }

  public byte[] getDataKey() {
    return this.dataKey;
  }

  public byte[] getIV() {
    return iv;
  }

  public CMK getCMK() {
    return this.cmk;
  }

  public String toString() {
    return
      String.format(
        "Message(data=0x%s, dataKey=0x%s, iv=0x%s, cmk=%s)",
        toHexString(data),
        toHexString(dataKey),
        toHexString(iv),
        cmk
      );
  }

  public Map<String, Object> toMap() {
    return Map.ofEntries(
      entry("data", toHexString(data)),
      entry("dataKey", toHexString(dataKey)),
      entry("iv", toHexString(iv)),
      entry("cmk", cmk.toMap())
    );
  }

  private String toHexString(final byte[] bytes) {
    if (bytes == null) {
      return null;
    }
    final StringBuilder hex = new StringBuilder(2 * bytes.length);
    for (final byte b : bytes) {
      final String hexCharacters = "0123456789ABCDEF";
      hex
        .append(hexCharacters.charAt((b & 0xF0) >> 4))
        .append(hexCharacters.charAt(b & 0x0F));
    }
    return hex.toString();
  }
}
