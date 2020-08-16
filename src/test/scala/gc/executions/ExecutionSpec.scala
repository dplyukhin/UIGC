package gc.executions

import org.scalacheck.Gen
import org.scalacheck.Gen._

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
      (1, genIdle(c)),
      (10, genSnapshot(c)),
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
      sender <- tryOneOf(c.busyActors)
      senderState = c.states(sender)

      recipientRef <- tryOneOf(senderState.activeRefs)
      recipient = recipientRef.target

      createdRefs <- containerOf[List, DummyRef](genRef(senderState))
      msg = AppMessage(createdRefs, recipientRef.token)

    } yield Send(sender, recipient, msg)
  }

  def genRef(actorState: DummyState): Gen[DummyRef] = {
    for {
      owner <- tryOneOf(actorState.activeRefs)
      target <- tryOneOf(actorState.activeRefs)
    } yield DummyRef(Some(DummyToken()), Some(owner.target), target.target)
  }

  def genReceive(c: Configuration): Gen[Receive] = {
    for {
      recipient <- tryOneOf(c.readyActors)
    } yield Receive(recipient)
  }

  def genIdle(c: Configuration): Gen[Idle] = {
    for {
      actor <- tryOneOf(c.busyActors)
    } yield Idle(actor)
  }

  def genDeactivate(c: Configuration): Gen[Deactivate] = {
    for {
      actor <- tryOneOf(c.busyActors)
      refs <- tryOneOf(c.states(actor).activeRefs) // pick reference to deactivate
    } yield Deactivate(actor, refs)
  }

  def genSnapshot(c: Configuration): Gen[Snapshot] = {
    for {
      idleActor <- tryOneOf(c.idleActors)
    } yield Snapshot(idleActor)
  }

  def genExecution(c: Configuration, size: Int): Gen[Execution] = {
    if (size <= 0)
      return const(Seq())

    // This expression tries generating an event and, if it succeeded,
    // advances the configuration and generates subsequent events.
    // TODO The mutability of Configuration looks fishy inside this `for` comprehension. Make it immutable?
    for {
      event <- genEvent(c)
      _ = c.transition(event)
      events <- genExecution(c, size - 1)
    } yield {
      Seq(event) ++ events
    }
  }

  def main(args: Array[String]): Unit = {

    val c: Configuration = Configuration()
    val mExecution: Option[Execution] = genExecution(c, 10).sample

    if (mExecution.isEmpty) {
      println("Failed to generate a legal execution.")
      return
    }
    val execution = mExecution.get

    println("Execution:")
    execution foreach println
    println("Configuration dump:\n" +
      s"Snapshots: ${c.snapshots}\n")
    println("Configuration dump:")
    println("States:")
    for ((name, state) <- c.states) {
      println(name)
      println(s"\tActive: ${state.activeRefs}")
      println(s"\tCreated: ${state.createdUsing}")
      println(s"\tOwners: ${state.owners}")
      println(s"\tReleased: ${state.releasedOwners}")
      println(s"\tSent: ${state.sentCount}")
      println(s"\tRecv: ${state.recvCount}")
      println(s"\tBusy?: ${c.busy(name)}")
      println(s"\tMessages: ${c.actors.map(c.pendingMessages)}")
    }
  }
}