package com.adaptris.profiler.prometheus;

import com.adaptris.core.ComponentLifecycleExtension;

public interface MessageMetricsCollector extends Runnable, MessageMetricsNotifier, ComponentLifecycleExtension {

}
