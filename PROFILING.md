# Profiling UIGC

UIGC is still a very new library. Here are some tips for profiling performance issues:

- Run the application with `-verbose:gc -XX:+PrintGCTimeStamps -XX:+PrintGCDetails` to see how much memory is being allocated.
- Use [async-profiler](https://github.com/async-profiler/async-profiler) to investigate CPU and memory bottlenecks.
