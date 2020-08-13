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
package uk.acmelabs.datasecurity.aws.metrics;

import java.util.Map;
import static java.util.Map.entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.metrics.MetricCollection;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.metrics.MetricRecord;
import static net.logstash.logback.argument.StructuredArguments.entries;

public final class LoggingMetricPublisher implements MetricPublisher {
  private static final Logger LOG =
    LoggerFactory.getLogger(uk.acmelabs.datasecurity.aws.metrics.LoggingMetricPublisher.class);

  private LoggingMetricPublisher() {
  }

  public static uk.acmelabs.datasecurity.aws.metrics.LoggingMetricPublisher create() {
    return new uk.acmelabs.datasecurity.aws.metrics.LoggingMetricPublisher();
  }

  @Override
  public void publish(MetricCollection metricCollection) {
    LOG.info("LoggingMetricPublisher.publish", entries(toMap(metricCollection)));
  }

  @Override
  public void close() {
  }

  private Map<String, Object> toMap(MetricCollection metricCollection) {
    return Map.ofEntries(
      entry("MetricCollection", Map.ofEntries(
        entry("name", metricCollection.name()),
        entry("creationTime", metricCollection.creationTime()),
        entry("metrics", metricCollection.stream().map(this::toMap).toArray()),
        entry("children", metricCollection.children().stream().map(this::toMap).toArray())
      ))
    );
  }

  private <T> Map<String, Object> toMap(MetricRecord<T> metricRecord) {
    return Map.ofEntries(
      entry("MetricRecord", Map.ofEntries(
        entry("value", metricRecord.value()),
        entry("name", metricRecord.metric().name())
      ))
    );
  }
}
