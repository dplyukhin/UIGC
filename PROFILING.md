# Profiling UIGC

UIGC is still a very new library. Here are some tips for profiling performance issues:

- Run the application with `-verbose:gc -XX:+PrintGCTimeStamps -XX:+PrintGCDetails` to see how much memory is being allocated.
- Use [async-profiler](https://github.com/async-profiler/async-profiler) to investigate CPU and memory bottlenecks.

UIGC also writes statistics to [Java Flight Recorder (JFR)](https://docs.oracle.com/javacomponents/jmc-5-4/jfr-runtime-guide/about.htm).
One way to view them is to download [Java Mission Control (JMC)](https://www.oracle.com/java/technologies/jdk-mission-control.html)
and start a new flight recording. Events related to actor garbage collection can be found in the event browser under the category "UIGC".