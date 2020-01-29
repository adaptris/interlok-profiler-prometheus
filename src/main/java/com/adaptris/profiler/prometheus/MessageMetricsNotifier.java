package com.adaptris.profiler.prometheus;

import java.util.List;

import com.adaptris.monitor.agent.activity.ActivityMap;

public interface MessageMetricsNotifier {

  public void registerListener(MessageMetricsListener listener);
  
  public void deregisterListener(MessageMetricsListener listener);
  
  public void notifyListeners(List<ActivityMap> stats);
  
}
