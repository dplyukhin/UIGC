package gc.properties.model

import gc.{AbstractSnapshot, ActorState}


class DummyState(self: DummyRef, creator: DummyRef)
  extends ActorState[DummyName, DummyToken, DummyRef, DummySnapshot](self, creator, DummySnapshot)

case class DummySnapshot(refs: Iterable[DummyRef],
                         created: Iterable[DummyRef],
                         owners: Iterable[DummyRef],
                         releasedRefs: Iterable[DummyRef],
                         sentCounts: Map[DummyToken, Int],
                         recvCounts: Map[DummyToken, Int])
  extends AbstractSnapshot[DummyName, DummyToken, DummyRef]
