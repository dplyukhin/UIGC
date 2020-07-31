package gc.executions

import org.scalacheck.Gen
import org.scalacheck.Gen._

object ExecutionSpec {

  case class Counters(names: Int = 0, tokens: Int = 0)

  def genRef(owner: DummyName, c: Counters): Gen[DummyRef] = {
    for {
      token <- choose(0, c.tokens)
      target <- choose(0, c.names)
    } yield DummyRef(Some(DummyToken(token)), Some(owner), DummyName(target))
  }

  def genEvent(c: Counters): Gen[(Event, Counters)] = {
    oneOf(
      genSpawn(c),
      genSend(c),
      genCreateRef(c),
      genReceive(c),
      genIdle(c),
      genSnapshot(c),
      genDeactivate(c)
    )
  }

  def genSpawn(c: Counters): Gen[(Spawn, Counters)] = {
    for {
      a <- choose(0, c.names)
      b <- choose(0, c.names + 1)
    } yield (Spawn(DummyName(a), DummyName(b)), c)
  }

  def genSend(c: Counters): Gen[(Send, Counters)] = {
    for {
      sender <- choose(0, c.names)
      recipient <- choose(0, c.names)
      // pick some references created by this sender
      refs <- containerOf[List, DummyRef](genRef(DummyName(sender), c))
      // generate some token for the hypothetical ref that this message would travel on
      token <- choose(0, c.tokens)
      msg = AppMessage(refs, Some(DummyToken(token)))
    } yield (Send(DummyName(sender), DummyName(recipient), msg), c)
  }

  def genReceive(c: Counters): Gen[(Receive, Counters)] = {
    // pick random actor with nonempty mailbox
    for {
      a <- choose(0, c.names)
    } yield (Receive(DummyName(a)), c)
  }

  def genCreateRef(c: Counters): Gen[(CreateRef, Counters)] = {
    for {
      // pick random names
      creator <- choose(0, c.names)
      owner <- choose(0, c.names)
      target <- choose(0, c.names)
      // pick random tokens for the creator's refs
      ownerToken <- choose(0, c.tokens)
      targetToken <- choose(0, c.tokens)
      // pick new token for the created ref
      newToken <- choose(0, c.tokens + 1)
      // assemble the refs
      ownerRef = DummyRef(Some(DummyToken(ownerToken)), Some(DummyName(creator)), DummyName(owner))
      targetRef = DummyRef(Some(DummyToken(targetToken)), Some(DummyName(creator)), DummyName(target))
    } yield (
      CreateRef(DummyName(creator), ownerRef, targetRef, DummyToken(newToken)),
      c.copy(tokens = c.tokens + 1)
    )
  }

  def genIdle(c: Counters): Gen[(Idle, Counters)] = {
    for {
      name <- choose(0, c.names)
    } yield (Idle(DummyName(name)), c)
  }

  def genDeactivate(c: Counters): Gen[(Deactivate, Counters)] = {
    for {
      name <- choose(0, c.names)
      ref <- genRef(DummyName(name), c)
    } yield (Deactivate(DummyName(name), ref), c)
  }

  def genSnapshot(c: Counters): Gen[(Snapshot, Counters)] = {
    for {
      name <- choose(0, c.names)
    } yield (Snapshot(DummyName(name)), c)
  }

  def chain(c: Counters, i: Int): Gen[Execution] = {
    if (i == 0) {
      for {
        (e, _) <- genEvent(c)
      } yield Seq(e)
    }
    else {
      for {
        (e, c2) <- genEvent(c)
        e2 <- chain(c2, i - 1)
      } yield Seq(e) ++ e2
    }
  }

  def genExecution(): Gen[Execution] = Gen.sized { size =>
    val c = Counters()
    chain(c, size)
  }
}