package gc.properties.model

import gc.AbstractRef


case class DummyRef(token: Option[DummyToken],
                    owner: Option[DummyName],
                    target: DummyName)
  extends AbstractRef[DummyName, DummyToken] {

  override def toString: String = {
    val tString: String = if (token.isDefined) token.get.n.toString else "∅"
    val oString: String = if (owner.isDefined) owner.get.n.toString else "∅"
    s"ref($tString: $oString -> ${target.n})"
  }
}


case class DummyToken(n: Int)

