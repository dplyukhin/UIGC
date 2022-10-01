package randomgraphs

object RandomGraphsConfig {
  /** M is a configurable fixed parameter that decides the number of action an Actor will perform */
  val NumberOfActions : Int = 10
  /** N is a configurable fixed parameter that decides the amount of Actors will be generated */
  val NumberOfSpawns : Int = 100000
  /** P1 is the probability for Action 1: Spawning an Actor */
  val ProbabilityToSpawn : Double = 0.01
  /** P2 is the probability for Action 2: Sending a ref from one Actor to another*/
  val ProbabilityToSendRef : Double = 0.15
  /** P3 is the probability for Action 3: Releasing refereces to Actors */
  val ProbabilityToReleaseRef : Double = 0.1
  /** P4 is the probability for Action 4: Sending Application Messages to Actors */
  val ProbabilityToPing : Double = 0.4
  /** P is the amount of ping messages that will be sent  */
  val NumberOfPingsSent : Int = 1000

}
