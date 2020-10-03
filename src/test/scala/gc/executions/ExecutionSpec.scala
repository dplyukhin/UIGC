package gc.executions

import gc.QuiescenceDetector
import org.scalacheck.Gen
import org.scalacheck.Gen._
import org.scalacheck.Prop._
import org.scalatest.propspec.AnyPropSpecLike

object ExecutionSpec {

  def tryOneOf[A](items: Iterable[A]): Gen[A] = {
    if (items.isEmpty) fail
    else oneOf(items)
  }

  def genEvent(c: Configuration): Gen[Event] = {
    // These generators use `tryOneOf`, which fails when given an empty sequence.
    // We want to keep trying to generate executions even when it fails, so it's
    // wrapped in `retryUntil` with a maximum of 100 attempts.
    frequency(
      (10, genSpawn(c)),
      (10, genSend(c)),
      (10, genReceive(c)),
      (10, genIdle(c)),
      (5, genSnapshot(c)),
      (5, genDeactivate(c))
    ).retryUntil(_ => true, 100)
  }

  def genSpawn(c: Configuration): Gen[Spawn] = {
    for {
      parent <- tryOneOf(c.busyActors)
      child = DummyName()
    } yield Spawn(parent, child)
  }

  def genSend(c: Configuration): Gen[Send] = {
    for {
      // pick a busy actor to send the message
      sender <- tryOneOf(c.busyActors)
      senderState = c.state(sender)

      // pick a recipient from its active refs
      recipientRef <- tryOneOf(senderState.activeRefs)
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
      createdUsingRef <- tryOneOf(actorState.activeRefs)
      newRef = DummyRef(Some(DummyToken()), Some(owner), createdUsingRef.target)
    } yield (newRef, createdUsingRef)
  }

  def genReceive(c: Configuration): Gen[Receive] = {
    for {
      recipient <- tryOneOf(c.readyActors)
    } yield Receive(recipient)
  }

  def genIdle(c: Configuration): Gen[BecomeIdle] = {
    for {
      actor <- tryOneOf(c.busyActors)
    } yield BecomeIdle(actor)
  }

  def genDeactivate(c: Configuration): Gen[Deactivate] = {
    for {
      actor <- tryOneOf(c.busyActors)
      refs <- tryOneOf(c.state(actor).activeRefs) // pick reference to deactivate
    } yield Deactivate(actor, refs)
  }

  def genSnapshot(c: Configuration): Gen[Snapshot] = {
    for {
      idleActor <- tryOneOf(c.idleActors)
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

      for {
        event <- genEvent(c)
        _ = c.transition(event)
      } yield Left(e :+ event, c, size - 1)

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

class ExecutionSpec extends AnyPropSpecLike {
  import ExecutionSpec._
  property("garbage actors must also be blocked") {
    val executionSize = 500
    forAll(genConfiguration(executionSize)) { (config: Configuration) => {
      config.garbageActors subsetOf config.blockedActors.toSet
    }}
  }

  property("potential inverse acquaintances of garbage must also be blocked") {
    val executionSize = 500
    forAll(genConfiguration(executionSize)) { (config: Configuration) => {
      val garbage = config.garbageActors
      val blocked = config.blockedActors.toSet
      garbage.forall(config.potentialInverseAcquaintances(_).toSet subsetOf blocked)
    }}
  }

  property("quiescent actors must be garbage") {
    val executionSize = 500
    val q: QuiescenceDetector[DummyName, DummyToken, DummyRef, DummySnapshot] = new QuiescenceDetector()
    forAll(genConfiguration(executionSize)) { (config: Configuration) => {
      var snaps: Map[DummyName, DummySnapshot] = Map()
      for ((name, snap) <- config.snapshots) {
        snaps += (name -> snap)
      }
      q.findTerminated(snaps) == config.garbageActors
    }}
  }
}