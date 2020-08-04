package gc.executions

import org.scalacheck.Gen
import org.scalacheck.Gen._

object ExecutionSpec{

  def genEvent(c: Configuration): Gen[Event] = {
    frequency(
      (10, genSpawn(c)),
      (10, genSend(c)),
      (10, genCreateRef(c)),
      (10, genReceive(c)),
      (1, genIdle(c)),
      (10, genSnapshot(c)),
      (5, genDeactivate(c))
    )
  }

  def genSpawn(c: Configuration): Gen[Spawn] = {
    val child = DummyName(DummyName.count + 1) // unique new name
    for {
      parent <- oneOf(c.states.keySet)
    } yield Spawn(parent, child)
  }

  def genSend(c: Configuration): Gen[Send] = {
    for {
      sender <- oneOf(c.states.keySet)
      senderState = c.states(sender)
      recipientRef <- oneOf(senderState.activeRefs)
      recipient = recipientRef.target
      // pick some references created by this sender
      createdRefs <- someOf(senderState.createdRefs.values.flatten.filter { ref => ref.owner.get == recipient })
      msg = AppMessage(createdRefs, recipientRef.token)
    } yield Send(sender, recipient, msg)
  }

  def genReceive(c: Configuration): Gen[Receive] = {
    // pick random actor with nonempty mailbox
    for {
      recipient <- oneOf(c.msgs.keySet) suchThat {name => c.msgs(name).nonEmpty}
    } yield Receive(recipient)
  }

  def genCreateRef(c: Configuration): Gen[CreateRef] = {
    for {
      creator <- oneOf(c.states.keySet) // pick random creator
      owner <- oneOf(c.states(creator).activeRefs) // pick random ref to new owner
      target <- oneOf(c.states(creator).activeRefs) // pick random ref to target
      token = DummyToken(DummyToken.count + 1) // unique token
    } yield CreateRef(creator, owner, target, token)
  }

  def genIdle(c: Configuration): Gen[Idle] = {
    val busyActors = c.busy.filter{case (_, busy) => busy}.keySet // get actors that are busy
    val emptyMailboxActors = c.msgs.filter{case (_, mailbox) => mailbox.isEmpty}.keySet // get actors with no msgs
    val boredActors = busyActors.intersect(emptyMailboxActors).toSeq
    if (boredActors.nonEmpty) {
      for {
        bored <- oneOf(boredActors)
      } yield Idle(bored)
    }
    else {
      Idle(DummyName(0))
    }
  }

  def genDeactivate(c: Configuration): Gen[Deactivate] = {
    for {
      actor <- oneOf(c.states.keySet) // pick random actor
      refs <- oneOf(c.states(actor).activeRefs) // pick reference to deactivate
    } yield Deactivate(actor, refs)
  }

  def genSnapshot(c: Configuration): Gen[Snapshot] = {
    for {
      idleActor <- oneOf(c.busy.keySet) suchThat(name => !c.busy(name)) // pick random idle actor
    } yield Snapshot(idleActor)
  }


  def genExecution(c: Configuration, size: Int): Gen[Execution] = {
    chain(c, size)
  }

  def genLegalEvent(c: Configuration): Gen[Event] = {
    genEvent(c) suchThat {e => c.isLegal(e)}
  }

  def chain(c: Configuration, i: Int): Gen[Execution] = {
    if (i <= 1) {
      for {
        e <- genLegalEvent(c)
      } yield {
        c.transition(e)
        Seq(e)
      }
    }
    else {
      for {
        e <- genLegalEvent(c)
        e2 <- chain(c, i - 1)
      } yield {
        c.transition(e)
        Seq(e) ++ e2
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val c = Configuration()
    var execution: Execution = Seq()

    var g = ExecutionSpec.genEvent(c) suchThat {e => c.isLegal(e)}

    for (n <- 1 to 10) {
//      println(s"Attempt $n")
      val sample = g.sample
      if (sample.isDefined) {
        val event = sample.get
//        println("Event: " + event)
        c.transition(event)
        execution :+= event
        g = ExecutionSpec.genEvent(c) retryUntil { e => c.isLegal(e) }
      }
      else {
        "Ran out of legal moves."
      }
    }
    println("Execution:")
    execution foreach {e => println(e)}
    println("Configuration dump:\n" +
      s"Sent refs: ${c.sentRefs}\n" +
      s"Snapshots: ${c.snapshots}\n")
    println("Configuration dump:")
    println("States:")
    for ((name, state) <- c.states) {
      println(name)
      println(s"\tActive: ${state.activeRefs}")
      println(s"\tCreated: ${state.createdRefs}")
      println(s"\tOwners: ${state.owners}")
      println(s"\tReleased: ${state.released}")
      println(s"\tSent: ${state.sent}")
      println(s"\tRecv: ${state.recv}")
      println(s"\tBusy?: ${c.busy(name)}")
      println(s"\tMessages: ${c.msgs(name)}")
    }
  }
}