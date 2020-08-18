/*
 * (c) 2020, Reed Business Information Limited
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
package com.adaptris.profiler.prometheus;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;

import com.adaptris.core.CoreException;
import com.adaptris.core.management.ManagementComponent;
import com.adaptris.monitor.agent.activity.ActivityMap;
import com.adaptris.monitor.agent.activity.AdapterActivity;
import com.adaptris.monitor.agent.activity.ChannelActivity;
import com.adaptris.monitor.agent.activity.ServiceActivity;
import com.adaptris.monitor.agent.activity.WorkflowActivity;
import com.adaptris.util.NumberUtils;

import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.PushGateway;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PrometheusMetricsAdapter implements MessageMetricsListener, ManagementComponent {
  
  private static final String WORKFLOW_MESSAGE_COUNT_METRIC = "workflowMessageCount";
  
  private static final String WORKFLOW_MESSAGE_COUNT_METRIC_HELP = "The number of messages processed by this workflow since the last time we checked.";
  
  private static final String WORKFLOW_MESSAGE_FAIL_COUNT_METRIC = "workflowMessageFailCount";
  
  private static final String WORKFLOW_MESSAGE_FAIL_COUNT_METRIC_HELP = "The number of messages processed by this workflow that have failed since the last time we checked.";
  
  private static final String WORKFLOW_AVG_TIME_NANOS_METRIC = "workflowAvgNanos";
  
  private static final String WORKFLOW_AVG_TIME_NANOS_METRIC_HELP = "The average amount of time in nanoseconds this workflow takes to process a single message.";
  
  private static final String WORKFLOW_AVG_TIME_MILLIS_METRIC = "workflowAvgMillis";
  
  private static final String WORKFLOW_AVG_TIME_MILLIS_METRIC_HELP = "The average amount of time in millisecods this workflow takes to process a single message.";
  
  private static final String SERVICE_AVG_TIME_NANOS_METRIC = "serviceAvgNanos";
  
  private static final String SERVICE_AVG_TIME_NANOS_METRIC_HELP = "The average amount of time in nanoseconds this service takes to process a single message.";
  
  private static final String SERVICE_AVG_TIME_MILLIS_METRIC = "serviceAvgMillis";
  
  private static final String SERVICE_AVG_TIME_MILLIS_METRIC_HELP = "The average amount of time in millisecods this service takes to process a single message.";
  
  private static final String PRODUCER_AVG_TIME_NANOS_METRIC = "producerAvgNanos";

  private static final String PRODUCER_AVG_TIME_NANOS_METRIC_HELP = "The average amount of time in nanoseconds this producer takes to process a single message.";
  
  private static final String PRODUCER_AVG_TIME_MILLIS_METRIC = "producerAvgMillis";

  private static final String PRODUCER_AVG_TIME_MILLIS_METRIC_HELP = "The average amount of time in milliseconds this producer takes to process a single message.";
    
  private static final String PROMETHEUS_ENDPOINT_KEY = "prometheusEndpointUrl";
  
  private static final Integer METRICS_COLLECTOR_INTERVAL_SECONDS_DEFAULT = 10;
  
  @Getter
  @Setter
  private Properties bootstrapProperties;
  
  @Getter
  @Setter
  private PushGateway pushGateway;
  
  @Getter
  @Setter
  private MessageMetricsCollector messageMetricsCollector;
  
  @Getter
  @Setter
  private Integer collectorIntervalSeconds;
  
  private ScheduledExecutorService scheduler;
  private ScheduledFuture<?> schedulerHandle;

  private ActivityMap lastActivityMap;
  
  public PrometheusMetricsAdapter() {
    MessageMetricsCollector metricsCollector = new JmxMessageMetricsCollector();
    metricsCollector.registerListener(this);
    this.setMessageMetricsCollector(metricsCollector);
    this.setBootstrapProperties(new Properties());
  }
  
  @Override
  public void init(Properties config) throws Exception {
    this.getMessageMetricsCollector().prepare();
    this.setBootstrapProperties(config);
    
    if(this.getPushGateway() == null) {
      if(this.getPrometheusEndpoint() != null)
        this.setPushGateway(new PushGateway(this.getPrometheusEndpoint()));
      else
        log.warn("Prometheus Metrics Adapter could not be started because the bootstrap property or system property {}, was not set.", PROMETHEUS_ENDPOINT_KEY);
    }
  }
  
  @Override
  public void start() throws CoreException {
    log.info("Prometheus profiling adapter is starting.");
    if(this.getPushGateway() != null) {
      scheduler = Executors.newScheduledThreadPool(1, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
          return new Thread(runnable, "Prometheus Metric Gatherer");
        }
      });
      
      this.schedulerHandle = this.scheduler.scheduleWithFixedDelay(
          this.getMessageMetricsCollector(), 
          this.collectorIntervalSeconds(), 
          this.collectorIntervalSeconds(), 
          TimeUnit.SECONDS);
    }
    log.info("Prometheus profiling adapter has started.");
  }
  
  @Override
  public void stop() {
    if(this.getPushGateway() != null) {
      if (schedulerHandle != null) {
        this.schedulerHandle.cancel(true);
        scheduler.shutdownNow();
      }
    }
  }
  
  private String getPrometheusEndpoint() {
    if(!StringUtils.isEmpty(System.getProperty(PROMETHEUS_ENDPOINT_KEY)))
      return System.getProperty(PROMETHEUS_ENDPOINT_KEY);
    if(this.getBootstrapProperties().getProperty(PROMETHEUS_ENDPOINT_KEY) != null)
      return this.getBootstrapProperties().getProperty(PROMETHEUS_ENDPOINT_KEY);
    
    return null;
  }
  
  protected int collectorIntervalSeconds() {
    return NumberUtils.toIntDefaultIfNull(this.getCollectorIntervalSeconds(), METRICS_COLLECTOR_INTERVAL_SECONDS_DEFAULT);
  }

  @Override
  public void notifyMessageMetrics(List<ActivityMap> statistics) {
    if(statistics.size() == 0) {
      log.trace("Hello!!!");
      if(lastActivityMap != null) {
        lastActivityMap.resetActivity();
        statistics.add(lastActivityMap);
      }
    }
      
    statistics.forEach(activityMap -> {
      CollectorRegistry registry = new CollectorRegistry();
      
      activityMap.getAdapters().forEach((adapterId, adapterActivity) -> {
        ((AdapterActivity) adapterActivity).getChannels().forEach((channelId, channelActivity) -> {
          ((ChannelActivity) channelActivity).getWorkflows().forEach((workflowId, workflowActivity) -> {
            
            this.buildAndRegisterMetric(WORKFLOW_MESSAGE_COUNT_METRIC, WORKFLOW_MESSAGE_COUNT_METRIC_HELP, 
                workflowId, null, registry, workflowActivity.getMessageCount());
            
            this.buildAndRegisterMetric(WORKFLOW_MESSAGE_FAIL_COUNT_METRIC, WORKFLOW_MESSAGE_FAIL_COUNT_METRIC_HELP, 
                workflowId, null, registry, workflowActivity.getFailedCount());
            
            this.buildAndRegisterMetric(WORKFLOW_AVG_TIME_NANOS_METRIC, WORKFLOW_AVG_TIME_NANOS_METRIC_HELP, 
                workflowId, null, registry, workflowActivity.getAvgNsTaken());
            
            this.buildAndRegisterMetric(WORKFLOW_AVG_TIME_MILLIS_METRIC, WORKFLOW_AVG_TIME_MILLIS_METRIC_HELP, 
                workflowId, null, registry, workflowActivity.getAvgMsTaken());
            
            this.buildAndRegisterMetric(PRODUCER_AVG_TIME_NANOS_METRIC, PRODUCER_AVG_TIME_NANOS_METRIC_HELP, 
                workflowId, workflowActivity.getProducerActivity().getUniqueId(), registry, workflowActivity.getProducerActivity().getAvgNsTaken());
            
            this.buildAndRegisterMetric(PRODUCER_AVG_TIME_MILLIS_METRIC, PRODUCER_AVG_TIME_MILLIS_METRIC_HELP, 
                workflowId, workflowActivity.getProducerActivity().getUniqueId(), registry, workflowActivity.getProducerActivity().getAvgMsTaken());
            
            ((WorkflowActivity) workflowActivity).getServices().forEach((serviceId, serviceActivity) -> {

              this.recursivelyBuildAndRegisterServiceMetric(workflowId, serviceActivity, registry);

            });
            
          });
          
          try {
            this.getPushGateway().pushAdd(registry, adapterActivity.getUniqueId());
          } catch (Exception ex) {
            log.warn("Could not push metrics to prometheus.  Continuing...");
          }
          
        });
      });
      
      this.lastActivityMap = activityMap;
    });
  }

  private void recursivelyBuildAndRegisterServiceMetric(String workflowId, ServiceActivity serviceActivity, CollectorRegistry registry) {
    this.buildAndRegisterMetric(SERVICE_AVG_TIME_NANOS_METRIC, SERVICE_AVG_TIME_NANOS_METRIC_HELP, 
        workflowId, serviceActivity.getUniqueId(), registry, serviceActivity.getAvgNsTaken());
    
    this.buildAndRegisterMetric(SERVICE_AVG_TIME_MILLIS_METRIC, SERVICE_AVG_TIME_MILLIS_METRIC_HELP, 
        workflowId, serviceActivity.getUniqueId(), registry, serviceActivity.getAvgMsTaken());
    
    serviceActivity.getServices().forEach((serviceId, childServiceActivity) -> {
      this.recursivelyBuildAndRegisterServiceMetric(workflowId, childServiceActivity, registry);
    });
    
  }

  private void buildAndRegisterMetric(String metricType, String metricHelp, String workflowId, String serviceId, CollectorRegistry registry, double value) {
    String metricName = buildMetricName(metricType, workflowId, serviceId);
    Gauge workflowMessageCountGauge = (Gauge) this.createMetric(metricName, metricHelp, registry);            
    workflowMessageCountGauge.set(value);
    
    log.trace("Pushing metric with name [{}] and value [{}]", metricName, value);
  }

  private Collector createMetric(String metricName, String helper, CollectorRegistry registry) {
    Gauge serviceAvgTimeMillisGauge = 
      Gauge
        .build()
        .name(metricName)
        .help(helper)
        .register(registry);
    
    return serviceAvgTimeMillisGauge;
  }
  
  private String buildMetricName(String metricType, String workflow, String service) {
    StringBuffer returnValue = new StringBuffer();
    returnValue.append(metricType);
    returnValue.append("_");
    returnValue.append(workflow.replace(".", "").replace("-", "").replace("_", ""));
    if(service != null) {
      returnValue.append("_");
      returnValue.append(service.replace(".", "").replace("-", "").replace("_", ""));  
    }
    
    return returnValue.toString();
  }
  
  @Override
  public void destroy() throws Exception {
    // TODO Auto-generated method stub
    
  }
  
}
