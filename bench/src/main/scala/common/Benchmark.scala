package common

import java.util._

trait Benchmark {
  val name: String
  def init(): Unit
  def run(): Unit 
  def cleanup(): Unit
}

object Benchmark {
  def runBenchmark(benchmark: Benchmark, warmupIterations: Int = 10, numIterations: Int = 20) = {
    var iterationTimes = Seq[Double]()
    println(benchmark.name)
  
    for (i <- 1 to (warmupIterations + numIterations)) {
  
      benchmark.init()
  
      val startTime = System.nanoTime()
      benchmark.run()
      val endTime = System.nanoTime()
  
      val execTimeMillis = (endTime - startTime) / 1e6
      if (i <= warmupIterations) {
        println(s"Warmup iteration $i: $execTimeMillis ms")
      } else {
        val j = i - warmupIterations
        iterationTimes = iterationTimes :+ execTimeMillis
        println(s"Iteration ${j}: $execTimeMillis ms")
      }
  
      benchmark.cleanup()
    }
  
    val avg = iterationTimes.sum / iterationTimes.length
    val min = iterationTimes.min
    val max = iterationTimes.max
    println(s"\nAverage: ${avg}\nMinimum: ${min}\nMaximum: ${max}")
  }
}

