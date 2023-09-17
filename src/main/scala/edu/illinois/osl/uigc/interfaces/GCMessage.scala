package edu.illinois.osl.uigc.interfaces

trait Message {
  def refs: Iterable[Refob[Nothing]]
}

trait NoRefs extends Message {
  override def refs: Seq[Nothing] = Nil
}

/** UIGC actors typically handle two types of messages: "Application messages" sent by the user's
  * application, and "control messages" sent by the GC engine. Control messages are handled
  * transparently by the UIGC middleware.
  *
  * The [[GCMessage]] type is a supertype of control messages and application messages.
  *
  * @tparam T
  *   The type of application messages
  */
trait GCMessage[+T] extends Message
