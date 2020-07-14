package gc.executions

case class Ref(token: Option[Token], owner: Option[Address], target: Address)

object Ref {
  def apply(owner: Address, target: Address): Ref = new Ref(Some(Token()), Some(owner), target)
}

case class Token(n: Int)

object Token {
  private var count = 0
  def apply(): Token = {
    val t = new Token(count)
    count += 1
    t
  }
}