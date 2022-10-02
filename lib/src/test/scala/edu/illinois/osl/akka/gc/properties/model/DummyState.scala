package edu.illinois.osl.akka.gc.properties.model

import edu.illinois.osl.akka.gc.ActorState
import edu.illinois.osl.akka.gc.detector.AbstractSnapshot


class DummyState(self: DummyRef, creator: DummyRef)
  extends ActorState[DummyName, DummyToken, DummyRef, DummySnapshot](self, creator, DummySnapshot)

case class DummySnapshot(refs: Iterable[DummyRef] = Set(),
                         owners: Iterable[DummyRef] = Set(),
                         created: Iterable[DummyRef] = Set(),
                         releasedRefs: Iterable[DummyRef] = Set(),
                         sentCounts: Map[DummyToken, Int] = Map(),
                         recvCounts: Map[DummyToken, Int] = Map(),
                        ) extends AbstractSnapshot[DummyName, DummyToken, DummyRef]
