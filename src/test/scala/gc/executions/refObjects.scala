package gc.executions


case class DummyRef(token: Option[DummyToken], owner: Option[DummyName], target: DummyName) {
  override def toString: String = {
    val tString: String = if (token.isDefined) token.get.n.toString else "∅"
    val oString: String = if (owner.isDefined) owner.get.n.toString else "∅"
    s"ref($tString: $oString -> ${target.n})"
  }
}

object DummyRef {
  def apply(owner: Option[DummyName], target: DummyName): DummyRef =
    if (owner.isDefined)
      new DummyRef(Some(DummyToken()), owner, target)
    else
      new DummyRef(None, None, target)
}


case class DummyToken(n: Int)

object DummyToken {
  var count = 0
  def apply(): DummyToken = {
    val t = new DummyToken(count)
    count += 1
    t
  }
}