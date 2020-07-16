package gc.executions

import gc.Message

/**
 *
 * @param states A map from actor addresses to their states.
 * @param msgs A map from actor adresses to bags of messages.
 * @param receptionists Actors that can receive messages from external actors.
 * @param externals Actors that are external to this GC configuration.
 */
case class Configuration(states: Map[DummyName, ActorState],
                         msgs: Map[DummyName, Set[Message]],
                         receptionists: Set[DummyName],
                         externals: Set[DummyName])

// TODO: write a better method for creating a Configuration from an existing Configuration

object Configuration {
  def apply(states: Map[DummyName, ActorState],
            msgs: Map[DummyName, Set[Message]],
            receptionists: Set[DummyName],
            externals: Set[DummyName]): Configuration = new Configuration(states, msgs, receptionists, externals)
  /**
   * Default constructor.
   * @return The initial configuration.
   */
  def apply(): Configuration = {
    val A = DummyName()
    val E = DummyName()
    val x = DummyRef(A, E)
    val y = DummyRef(A, A)
    val aState = ActorState(activeRefs = Set(x, y), owners = Set(y))
    new Configuration(
      states = Map(A -> aState),
      msgs = Map(),
      receptionists = Set(),
      externals = Set(E)
    )
  }

  def isBusy(c: Configuration, a: DummyName): Boolean = {
    c.states(a).busy
  }

  def transition(c: Configuration, e: Event): Configuration = {
    e match {
      case Spawn(parent, child) => {
        // create new references
        val x = DummyRef(parent, child) // parent's ref to child
        val y = DummyRef(child, child) // child's self-ref
        // create child's state
        val childState = ActorState(activeRefs = Set(y), owners = Set(x, y))
        // add the new active ref to the parent's state
        val parentState = c.states(parent) + ActorState(activeRefs = Set(x))
        // create the new configuration
        Configuration(c.states + (child -> childState, parent -> parentState), c.msgs, c.receptionists, c.externals)
      }
      case Send() => ???
      case Receive() => ???
      case Idle() =>  ???
      case SendInfo() => ???
      case Info() => ???
      case SendRelease() => ???
      case Release() => ???
    }
  }
}
