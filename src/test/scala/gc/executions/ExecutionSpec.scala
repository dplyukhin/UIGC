package gc.executions

import org.scalacheck.Gen
import org.scalacheck.Gen._

object ExecutionSpec {

  def genEvent(c: Configuration): Gen[Event] = {
    oneOf(
      genSpawn(c),
      genSend(c),
      genCreateRef(c),
      genReceive(c),
      genIdle(c),
      genSnapshot(c),
      genDeactivate(c)) suchThat (e => c.isLegal(e))
  }

  def genSpawn(c: Configuration): Gen[Spawn] = {
    val child = DummyName()
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
      // pick some references created by this sender for the recipient that haven't been sent yet
      createdRefs <- someOf(senderState.createdRefs.values.flatten filter {ref =>
        ref.owner.get == recipient && !c.sentRefs.contains(ref.token.get)})
      msg = AppMessage(createdRefs, recipientRef.token)
    } yield Send(sender, recipient, msg)
  }

  def genReceive(c: Configuration): Gen[Receive] = {
    // pick random actor with nonempty mailbox
    for {
      recipient <- oneOf(c.msgs.keySet) suchThat {name => c.msgs(name).isEmpty}
    } yield Receive(recipient)
  }

  def genCreateRef(c: Configuration): Gen[CreateRef] = {
    for {
      creator <- oneOf(c.states.keySet)
      owner <- oneOf(c.states(creator).activeRefs)
      target <- oneOf(c.states(creator).activeRefs)
    } yield CreateRef(creator, owner, target, DummyToken())
  }

  def genIdle(c: Configuration): Gen[Idle] = {
    val busyActors = c.busy.filter{case (_, busy) => busy}.keySet
    val emptyMailboxActors = c.msgs.filter{case (_, mailbox) => mailbox.isEmpty}.keySet
    for {
      bored <- oneOf(busyActors.intersect(emptyMailboxActors).toSeq)
    } yield Idle(bored)
  }

  def genDeactivate(c: Configuration): Gen[Deactivate] = {
    for {
      actor <- oneOf(c.states.keySet)
      refs <- someOf(c.states(actor).activeRefs)
    } yield Deactivate(actor, refs)
  }

  def genSnapshot(c: Configuration): Gen[Snapshot] = {
    for {
      idleActor <- oneOf(c.busy.keySet) suchThat(name => !c.busy(name))
    } yield Snapshot(idleActor)
  }


//  def genExecution(): Gen[Execution] = Gen.sized { size =>
//    val c = Configuration()
//    for {
//      event <- genEvent(c)
//    } yield event
//      c.transition(event)
////    Gen.listOfN(size, genEvent(starter))
//  }
}