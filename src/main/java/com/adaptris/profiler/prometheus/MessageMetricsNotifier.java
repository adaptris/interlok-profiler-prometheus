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

import com.adaptris.monitor.agent.activity.ActivityMap;

public interface MessageMetricsNotifier {

  public void registerListener(MessageMetricsListener listener);
  
  public void deregisterListener(MessageMetricsListener listener);
  
  public void notifyListeners(List<ActivityMap> stats);
  
}
