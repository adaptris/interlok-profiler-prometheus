package com.adaptris.profiler.prometheus;

import java.util.ArrayList;
import java.util.List;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.adaptris.core.CoreException;
import com.adaptris.core.util.JmxHelper;
import com.adaptris.monitor.agent.activity.ActivityMap;
import com.adaptris.monitor.agent.jmx.ProfilerEventClientMBean;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JmxMessageMetricsCollector implements MessageMetricsCollector {
   
  private static final String METRICS_OBJECT_NAME = "com.adaptris:type=Profiler";  
  
  @Getter
  @Setter
  private MBeanServer interlokMBeanServer;
  
  @Getter
  @Setter
  private ProfilerEventClientMBean profilerEventClient;
  
  @Getter
  @Setter
  private List<MessageMetricsListener> listeners;
  
  public JmxMessageMetricsCollector() {
    this.setListeners(new ArrayList<>());
  }
  
  @Override
  public void run() {
    log.trace("Checking ActivityMap from profiler.");
    List<ActivityMap> foundStatistics = new ArrayList<ActivityMap>();
    try {
      if(this.getInterlokMBeanServer() == null)
        this.setInterlokMBeanServer(JmxHelper.findMBeanServer());
      
      if(this.getProfilerEventClient() == null)
        loadProfilerEventClient();
    
      ActivityMap eventActivityMap = this.getProfilerEventClient().getEventActivityMap();
      while(eventActivityMap != null) {
        foundStatistics.add(eventActivityMap);
        eventActivityMap = this.getProfilerEventClient().getEventActivityMap();
      }
      
      this.notifyListeners(foundStatistics);
      
    } catch (Exception ex) {
      log.warn("Error collecting message profiler metrics from JMX, continuing...", ex);
    }
    
  }

  private void loadProfilerEventClient() throws Exception {
    if(this.getInterlokMBeanServer() == null)
      this.setInterlokMBeanServer(JmxHelper.findMBeanServer());
    
    ProfilerEventClientMBean profilerEventClientMBean = JMX.newMBeanProxy(getInterlokMBeanServer(), new ObjectName(METRICS_OBJECT_NAME), ProfilerEventClientMBean.class);
    if(profilerEventClientMBean != null)
      this.setProfilerEventClient(profilerEventClientMBean);
  }

  @Override
  public void prepare() throws CoreException {
    // Incase someone restarts the adapter after adding new mbeans
    // Let's reset everything
    this.setInterlokMBeanServer(null);
    this.setProfilerEventClient(null);
  }

  @Override
  public void registerListener(MessageMetricsListener listener) {
    this.getListeners().add(listener);
  }

  @Override
  public void deregisterListener(MessageMetricsListener listener) {
    this.getListeners().remove(listener);
  }

  @Override
  public void notifyListeners(List<ActivityMap> stats) {
    for(MessageMetricsListener listener : this.getListeners())
      listener.notifyMessageMetrics(stats);
  }

}
