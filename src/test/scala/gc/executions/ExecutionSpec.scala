package gc.executions

import gc.QuiescenceDetector
import org.scalacheck.{Gen, Properties, Test}
import org.scalacheck.Gen._
import org.scalacheck.Prop._
import org.scalacheck.util.ConsoleReporter

object ExecutionSpec {

  /**
   * Generates an event that can legally be executed in the given configuration.
   * Generates `None` if no more events are possible.
   */
  def genEvent(c: Configuration): Gen[Option[Event]] = {

    var generators: Seq[(Int, Gen[Event])] = Seq()

    // add each generator to the collection if its precondition is satisfied
    if (c.busyActors.nonEmpty)           generators :+= (10, genSpawn(c))
    if (c.busyActors.nonEmpty)           generators :+= (10, genSend(c))
    if (c.busyActors.nonEmpty)           generators :+= (10, genIdle(c))
    if (c.idleActors.nonEmpty)           generators :+= (5, genSnapshot(c))
    if (c.readyActors.nonEmpty)          generators :+= (10, genReceive(c))
    if (c.actorsWithActiveRefs.nonEmpty) generators :+= (10, genDeactivate(c))

    if (generators.isEmpty)
      const(None)
    else
      some(frequency(generators:_*))
  }

  def genSpawn(c: Configuration): Gen[Spawn] = {
    for {
      parent <- oneOf(c.busyActors)
      child = DummyName()
    } yield Spawn(parent, child)
  }

  def genSend(c: Configuration): Gen[Send] = {
    for {
      // pick a busy actor to send the message
      sender <- oneOf(c.busyActors)
      senderState = c.state(sender)

      // pick a recipient from its active refs
      recipientRef <- oneOf(senderState.activeRefs)
      recipient = recipientRef.target

      // generate a collection of refs that will be owned by the recipient
      newAcquaintances <- containerOf[List, (DummyRef, DummyRef)](genRef(senderState, recipient))
      (createdRefs, createdUsingRefs) = newAcquaintances.unzip

    } yield Send(sender, recipientRef, createdRefs, createdUsingRefs)
  }

  /**
   * Generates a new ref owned by `owner`.
   * @return Generator for a pair. The first element is the new ref, and the second element
   *         is the ref that was used to create it. That is, the second element is one of
   *         the creator's active refs pointing to the target of the new ref.
   */
  def genRef(actorState: DummyState, owner: DummyName): Gen[(DummyRef, DummyRef)] = {
    for {
      createdUsingRef <- oneOf(actorState.activeRefs)
      newRef = DummyRef(Some(DummyToken()), Some(owner), createdUsingRef.target)
    } yield (newRef, createdUsingRef)
  }

  def genReceive(c: Configuration): Gen[Receive] = {
    for {
      recipient <- oneOf(c.readyActors)
    } yield Receive(recipient)
  }

  def genIdle(c: Configuration): Gen[BecomeIdle] = {
    for {
      actor <- oneOf(c.busyActors)
    } yield BecomeIdle(actor)
  }

  def genDeactivate(c: Configuration): Gen[Deactivate] = {
    for {
      actor <- oneOf(c.actorsWithActiveRefs)
      state = c.state(actor)
      refs <- oneOf(state.activeRefs - state.selfRef) // pick a reference to deactivate
    } yield Deactivate(actor, refs)
  }

  def genSnapshot(c: Configuration): Gen[Snapshot] = {
    for {
      idleActor <- oneOf(c.idleActors)
    } yield Snapshot(idleActor)
  }

  def genExecutionAndConfiguration(executionSize: Int): Gen[(Execution, Configuration)] = {
    val config = Configuration()

    // This function takes:
    // (a) the execution generated so far,
    // (b) the configuration generated so far, and
    // (c) the remaining number of events to generate.
    // When there is nothing left to generate, it returns Right.
    // Otherwise, it generates one event, adds it to the execution, and returns Left.
    def helper(triple: (Execution, Configuration, Int)): Gen[Either[(Execution, Configuration, Int), (Execution, Configuration)]] = {
      val (e, c, size) = triple
      if (size <= 0)
        return const(Right(e,c))

      genEvent(c).flatMap {
        case None =>
          const(Right(e,c))
        case Some(event) =>
          c.transition(event)
          Left(e :+ event, c, size - 1)
      }
    }
    tailRecM[(Execution, Configuration, Int), (Execution, Configuration)]((Seq(), config, executionSize))(helper)
  }

  def genConfiguration(executionLength: Int): Gen[Configuration] = {
    for {
      (_, config) <- genExecutionAndConfiguration(executionLength)
    } yield config
  }
  def genExecution(executionLength: Int): Gen[Execution] = {
    for {
      (exec, _) <- genExecutionAndConfiguration(executionLength)
    } yield exec
  }

  def pain(args: Array[String]): Unit = {
    val mExecution: Option[(Execution, Configuration)] = genExecutionAndConfiguration(1000).sample

    if (mExecution.isEmpty) {
      println("Failed to generate a legal execution.")
      return
    }
    val (execution, c) = mExecution.get

    println("Execution:")
    execution foreach println

    println("Configuration dump:\n" +
      s"Snapshots: ${c.snapshots}\n")
    println("States:")
    for ((name, state) <- c.state) {
      println(name)
      println(s"\tActive: ${state.activeRefs}")
      println(s"\tCreated: ${state.createdUsing}")
      println(s"\tOwners: ${state.owners}")
      println(s"\tReleased: ${state.releasedOwners}")
      println(s"\tSent: ${state.sentCount}")
      println(s"\tRecv: ${state.recvCount}")
      println(s"\tBusy?: ${c.status(name)}")
      println(s"\tMessages: ${c.pendingMessages(name)}")
    }
    println("Blocked:")
    println(s"\t${c.blockedActors}")
    println("Garbage:")
    println(s"\t${c.garbageActors}")

    println("Quiescent detection:")
    val q: QuiescenceDetector[DummyName, DummyToken, DummyRef, DummySnapshot] = new QuiescenceDetector()
    var snaps: Map[DummyName, DummySnapshot] = Map()
    for ((name, snap) <- c.snapshots) {
      snaps += (name -> snap)
    }
    println("Candidate snapshots:")
    for ((name, snap) <- snaps) {
      println(s"\t$name -> $snap")
    }
    println("Quiescent detection results:")
    println(s"\t${q.findTerminated(snaps)}")
  }
}

object Spec extends Properties("Basic properties of executions") {
  import ExecutionSpec._

  val executionSize = 100
  override def overrideParameters(p: Test.Parameters): Test.Parameters =
    p.withMinSuccessfulTests(100)
      // This prevents Scalacheck console output from getting wrapped at 75 chars
      .withTestCallback(ConsoleReporter(1, Int.MaxValue))

  property("garbage actors must also be blocked") =
    forAll(genConfiguration(executionSize)) { (config: Configuration) => {
      config.garbageActors subsetOf config.blockedActors.toSet
    }}

  property("potential inverse acquaintances of garbage must also be blocked") =
    forAll(genConfiguration(executionSize)) { (config: Configuration) => {
      val garbage = config.garbageActors
      val blocked = config.blockedActors.toSet
      garbage.forall(config.potentialInverseAcquaintances(_).toSet subsetOf blocked)
    }}

  property("quiescent actors must be garbage") =
    forAll(genConfiguration(executionSize)) { (config: Configuration) => {
      val q: QuiescenceDetector[DummyName, DummyToken, DummyRef, DummySnapshot] =
        new QuiescenceDetector()

      q.findTerminated(config.snapshots.toMap) == config.garbageActors
    }}
}
