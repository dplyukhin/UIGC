package edu.illinois.osl.uigc.interfaces

trait Message {
  def refs: Iterable[RefobLike[Nothing]]
}

trait NoRefs extends Message {
  override def refs = Nil
}
