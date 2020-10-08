package gc.properties.model

import org.scalacheck.{Gen, Shrink}
import org.scalacheck.Gen._
import org.scalacheck.Shrink.shrink

object Generators {

  /**
   * Generates an event that can legally be executed in the given configuration.
   * Generates `None` if no more events are possible.
   */
  def genEvent(c: Configuration): Gen[Option[Event]] = {

    var generators: Seq[(Int, Gen[Event])] = Seq()

    // add each generator to the collection if its precondition is satisfied
    if (c.busyActors.nonEmpty)                      generators :+= (10, genSpawn(c))
    if (c.busyActors.nonEmpty)                      generators :+= (10, genSend(c))
    if (c.busyActors.nonEmpty)                      generators :+= (20, genIdle(c))
    if (c.readyActors.nonEmpty)                     generators :+= (10, genReceive(c))
    if (c.actorsThatCanTakeASnapshot.nonEmpty)      generators :+= (10, genSnapshot(c))
    if (c.actorsThatCanDeactivate.nonEmpty)         generators :+= (5, genDeactivate(c))

    if (generators.isEmpty)
      const(None)
    else
      some(frequency(generators:_*))
  }

  def genSpawn(c: Configuration): Gen[Spawn] = {
    for {
      parent <- oneOf(c.busyActors)
      child = c.DummyName()
      creatorRef = c.DummyRef(Some(parent), child)
      selfRef = c.DummyRef(Some(child), child)
    } yield Spawn(parent, child, creatorRef, selfRef)
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
      n <- choose(0,3)
      newAcquaintances <- containerOfN[List, (DummyRef, DummyRef)](n,genRef(c, senderState, recipient))
      (createdRefs, createdUsingRefs) = newAcquaintances.unzip

    } yield Send(sender, recipientRef, createdRefs, createdUsingRefs)
  }

  /**
   * Generates a new ref owned by `owner`.
   * @return Generator for a pair. The first element is the new ref, and the second element
   *         is the ref that was used to create it. That is, the second element is one of
   *         the creator's active refs pointing to the target of the new ref.
   */
  def genRef(c: Configuration, actorState: DummyState, owner: DummyName): Gen[(DummyRef, DummyRef)] = {
    for {
      createdUsingRef <- oneOf(actorState.activeRefs)
      newRef = DummyRef(Some(c.DummyToken()), Some(owner), createdUsingRef.target)
    } yield (newRef, createdUsingRef)
  }

  def genReceive(c: Configuration): Gen[Receive] = {
    for {
      recipient <- oneOf(c.readyActors)
      sender <- oneOf(c.pendingMessages(recipient).senders)
    } yield Receive(recipient, sender)
  }

  def genIdle(c: Configuration): Gen[BecomeIdle] = {
    for {
      actor <- oneOf(c.busyActors)
    } yield BecomeIdle(actor)
  }

  def genDeactivate(c: Configuration): Gen[Deactivate] = {
    for {
      actor <- oneOf(c.actorsThatCanDeactivate)
      state = c.state(actor)
      refs <- oneOf(state.activeRefs - state.selfRef) // pick a reference to deactivate
    } yield Deactivate(actor, refs)
  }

  def genSnapshot(c: Configuration): Gen[Snapshot] = {
    for {
      idleActor <- oneOf(c.actorsThatCanTakeASnapshot)
    } yield Snapshot(idleActor)
  }

  private def genExecutionAndConfiguration(
    executionSize: Int,
    initialConfig: Configuration = new Configuration(),
    minAmountOfGarbage: Int = 0,
  ): Gen[(Execution, Configuration)] = {
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
    tailRecM[(Execution, Configuration, Int), (Execution, Configuration)]((Seq(), initialConfig, executionSize))(helper)
      .suchThat { case (_, config) => config.garbageActors.size >= minAmountOfGarbage }
  }

  def genConfiguration(
    executionLength: Int,
    initialConfig: Configuration = new Configuration(),
    minAmountOfGarbage: Int = 0,
  ): Gen[Configuration] = {
    for {
      (_, config) <- genExecutionAndConfiguration(executionLength, initialConfig, minAmountOfGarbage)
    } yield config
  }
  def genExecution(
    executionLength: Int,
    initialConfig: Configuration = new Configuration(),
    minAmountOfGarbage: Int = 0
  ): Gen[Execution] = {
    for {
      (exec, _) <- genExecutionAndConfiguration(executionLength, initialConfig, minAmountOfGarbage)
    } yield exec
  }

  implicit def shrinkEvent(event: Event): Shrink[Event] = Shrink {
    case Send(sender, recipientRef, createdRefs, createdUsingRefs) =>
      // To shrink a `send` event, shrink the createdRefs and the createdUsingRefs
      val pairs = createdRefs.zip(createdUsingRefs)
      for {
        shrunkPairs <- shrink(pairs)
        (createdRefs, createdUsingRefs) = shrunkPairs.unzip
      } yield Send(sender, recipientRef, createdRefs, createdUsingRefs)

    case _ =>
      // No way to shrink the other events
      Stream.empty
  }

  def executionShrinkStream(execution: Execution): Stream[Execution] = {
    if (execution.isEmpty) return Stream.empty

    val last = execution.last
    val prefix = execution.slice(0, execution.length - 1)

    // We try to shrink the execution in the following ways:
    // 1. Remove the last element
    // 2. Shrink the last element
    // 3. Remove or shrink one of the preceding elements

    val omitLast = execution.dropRight(1)

    val shrinkLast =
      for (shrunkEvent <- shrink(last))
        yield execution.updated(execution.length - 1, shrunkEvent)

    val shrinkRest =
      if (prefix.isEmpty)
        Stream.empty
      else
        for (shrunkExecution <- executionShrinkStream(prefix))
          yield shrunkExecution :+ last

    Stream.cons(omitLast, shrinkLast).append(shrinkRest)
  }

  implicit val shrinkExecution: Shrink[Execution] = Shrink(execution =>
    executionShrinkStream(execution).filter(isLegal) // Only accept legal shrunken executions
  )

  implicit val shrinkConfiguration: Shrink[Configuration] = Shrink(config => {
    shrink(config.execution).map(Configuration.fromExecution)
  })

  private def isLegal(execution: Execution): Boolean = {
    try {
      val c = new Configuration()
      for (event <- execution) {
        c.transition(event)
      }
      true
    }
    catch {
      case _:AssertionError | _:Throwable =>
        false
    }
  }
}

