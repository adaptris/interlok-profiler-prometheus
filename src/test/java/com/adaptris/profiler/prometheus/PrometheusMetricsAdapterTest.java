package com.adaptris.profiler.prometheus;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.management.ObjectName;

import org.awaitility.Durations;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.adaptris.core.runtime.AdapterComponentMBean;
import com.adaptris.core.util.JmxHelper;
import com.adaptris.monitor.agent.activity.ActivityMap;
import com.adaptris.monitor.agent.activity.AdapterActivity;
import com.adaptris.monitor.agent.activity.ChannelActivity;
import com.adaptris.monitor.agent.activity.ConsumerActivity;
import com.adaptris.monitor.agent.activity.ProducerActivity;
import com.adaptris.monitor.agent.activity.ServiceActivity;
import com.adaptris.monitor.agent.activity.WorkflowActivity;
import com.adaptris.monitor.agent.jmx.ProfilerEventClient;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.PushGateway;

public class PrometheusMetricsAdapterTest {
  
  private PrometheusMetricsAdapter metricsAdapter;
  
  private ProfilerEventClient eventMBean;
  
  private Properties bootstrapProperties;
  
  @Mock private PushGateway mockPushGateway;
  
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    
    bootstrapProperties = new Properties();
    bootstrapProperties.put("prometheusEndpointUrl", "localhost:9091");
    
    metricsAdapter = new PrometheusMetricsAdapter();
    metricsAdapter.setCollectorIntervalSeconds(1);
    metricsAdapter.setPushGateway(mockPushGateway);
    
    eventMBean = new ProfilerEventClient();
    ObjectName mbeanName = new ObjectName(AdapterComponentMBean.JMX_DOMAIN_NAME + ":type=Profiler");
    JmxHelper.register(mbeanName, eventMBean);
    
    metricsAdapter.init(bootstrapProperties);
    metricsAdapter.start();
  }

  @After
  public void tearDown() throws Exception {
    metricsAdapter.stop();
    metricsAdapter.destroy();
    
  }
  
  @Test
  public void testInitCreatesPushgateway() throws Exception {
    metricsAdapter.setPushGateway(null);
    metricsAdapter.init(bootstrapProperties);
    
    assertNotNull(metricsAdapter.getPushGateway());
  }
  
  @Test
  public void testInitCreatesPushgatewaySystemProperty() throws Exception {
    System.setProperty("prometheusEndpointUrl", "localhost:9091");
    
    metricsAdapter.setPushGateway(null);
    metricsAdapter.init(new Properties());
    
    assertNotNull(metricsAdapter.getPushGateway());
  }
  
  @Test
  public void testStartupNoMetrics() throws Exception {
    metricsAdapter.getMessageMetricsCollector().registerListener(new MessageMetricsListener() {
      @Override
      public void notifyMessageMetrics(List<ActivityMap> statistics) {
        fail("There are no metrics, should not be called.");
      }
    });
    
    // Wait for 5 seconds, unless a metric comes in fails the test.
    await()
      .atLeast(Durations.ONE_HUNDRED_MILLISECONDS)
      .atMost(Durations.FIVE_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS);
  }
  
  @Test
  public void testConsumesMetric() throws Exception {
    eventMBean.addEventActivityMap(this.createActivityMap());
    
    final List<ActivityMap> receivedStats = new ArrayList<ActivityMap>();
    metricsAdapter.getMessageMetricsCollector().registerListener(new MessageMetricsListener() {
      @Override
      public void notifyMessageMetrics(List<ActivityMap> statistics) {
        receivedStats.addAll(statistics);
      }
    });
    
    // Wait for 5 seconds, Fail if we don't get the received message after that time.
    await()
      .atLeast(Durations.ONE_HUNDRED_MILLISECONDS)
      .atMost(Durations.FIVE_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .until(() -> receivedStats.size() == 1);
  }
  
  @Test
  public void testConsumesMultipleMetricsAfterOnlyPostingOne() throws Exception {
    eventMBean.addEventActivityMap(this.createActivityMap());
    
    final List<ActivityMap> receivedStats = new ArrayList<ActivityMap>();
    metricsAdapter.getMessageMetricsCollector().registerListener(new MessageMetricsListener() {
      @Override
      public void notifyMessageMetrics(List<ActivityMap> statistics) {
        receivedStats.addAll(statistics);
      }
    });
    
    // Wait for 10 seconds, Fail if we don't get the two received messages after that time.
    await()
      .atLeast(Durations.ONE_HUNDRED_MILLISECONDS)
      .atMost(Durations.TEN_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS)
      .until(() -> receivedStats.size() == 2);
  }
  
  @Test
  public void testConsumesMultipleMetricsPushgatewayNotAvailable() throws Exception {
    doThrow(new IOException("expected"))
      .when(mockPushGateway).pushAdd(any(CollectorRegistry.class), any(String.class));

    eventMBean.addEventActivityMap(this.createActivityMap());
    
    final List<ActivityMap> receivedStats = new ArrayList<ActivityMap>();
    metricsAdapter.getMessageMetricsCollector().registerListener(new MessageMetricsListener() {
      @Override
      public void notifyMessageMetrics(List<ActivityMap> statistics) {
        receivedStats.addAll(statistics);
      }
    });
    
    // Wait for 5 seconds, no exceptions should be thrown, even if pushgateway is unavailable..
    await()
      .atLeast(Durations.ONE_HUNDRED_MILLISECONDS)
      .atMost(Durations.FIVE_SECONDS)
    .with()
      .pollInterval(Durations.ONE_HUNDRED_MILLISECONDS);
  }

  
  private ActivityMap createActivityMap() throws Exception {
    ActivityMap returnValue = new ActivityMap();
    
    AdapterActivity adapterActivity = new AdapterActivity();
    adapterActivity.setUniqueId("Adapter");
    
    ChannelActivity channelActivity = new ChannelActivity();
    channelActivity.setUniqueId("Channel");
    
    WorkflowActivity workflowActivity = new WorkflowActivity();
    workflowActivity.setUniqueId("Workflow");
    
    ProducerActivity producerActivity = new ProducerActivity();
    producerActivity.setUniqueId("Producer");
    
    ConsumerActivity consumerActivity = new ConsumerActivity();
    consumerActivity.setUniqueId("Consumer");
    
    ServiceActivity serviceActivity1 = new ServiceActivity();
    serviceActivity1.setUniqueId("Service1");
    
    ServiceActivity serviceActivity2 = new ServiceActivity();
    serviceActivity2.setUniqueId("Service2");
    
    workflowActivity.setProducerActivity(producerActivity);
    workflowActivity.setConsumerActivity(consumerActivity);
    workflowActivity.getServices().put("Service1", serviceActivity1);
    serviceActivity1.getServices().put("Service2", serviceActivity2);
    
    channelActivity.getWorkflows().put("Workflow", workflowActivity);
    
    adapterActivity.getChannels().put("Channel", channelActivity);
    
    returnValue.getAdapters().put("Adapter", adapterActivity);
    
    return returnValue;
  }
  
}
