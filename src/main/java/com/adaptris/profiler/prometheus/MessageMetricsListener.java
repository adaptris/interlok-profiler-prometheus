package com.adaptris.profiler.prometheus;

import java.util.List;

import com.adaptris.monitor.agent.activity.ActivityMap;

public interface MessageMetricsListener {

  public void notifyMessageMetrics(List<ActivityMap> statistics);
  
}
