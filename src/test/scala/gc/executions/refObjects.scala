package gc.executions


case class DummyRef(token: Option[DummyToken], owner: Option[DummyName], target: DummyName)

object DummyRef {
  def apply(owner: DummyName, target: DummyName): DummyRef =
    new DummyRef(Some(DummyToken()), Some(owner), target)
}

case class DummyToken(n: Int)

object DummyToken {
  private var count = 0
  def apply(): DummyToken = {
    val t = new DummyToken(count)
    count += 1
    t
  }
}