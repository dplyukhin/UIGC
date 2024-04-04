package edu.illinois.osl.uigc.engines.crgc

import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.wordspec.AnyWordSpecLike

class SerializationSpec extends AnyWordSpecLike {

  "Delta Shadows" must {
    "serialize and deserialize correctly - test 1" in {
      val shadow = new DeltaShadow()
      shadow.recvCount = 1
      shadow.supervisor = 2
      shadow.interned = true
      shadow.isRoot = false
      shadow.isBusy = true
      shadow.outgoing.put(1.toShort, 2)
      shadow.outgoing.put(3.toShort, 4)

      // Create an output stream, serialize the shadow, then deserialize it
      val out = new java.io.ByteArrayOutputStream()
      val oos = new java.io.ObjectOutputStream(out)
      val bytesWritten = shadow.serialize(oos)
      bytesWritten shouldEqual 25
      oos.close()
      val in = new java.io.ByteArrayInputStream(out.toByteArray)
      val ois = new java.io.ObjectInputStream(in)
      val shadow2 = new DeltaShadow()
      shadow2.deserialize(ois)

      // Check that shadow and shadow2 have the same properties
      shadow2.recvCount shouldEqual shadow.recvCount
      shadow2.supervisor shouldEqual shadow.supervisor
      shadow2.interned shouldEqual shadow.interned
      shadow2.isRoot shouldEqual shadow.isRoot
      shadow2.isBusy shouldEqual shadow.isBusy
      shadow2.outgoing shouldEqual shadow.outgoing
    }

    "serialize and deserialize correctly - test 2" in {
      val shadow = new DeltaShadow()
      shadow.recvCount = 2
      shadow.supervisor = 0
      shadow.interned = false
      shadow.isRoot = true
      shadow.isBusy = false

      // Create an output stream, serialize the shadow, then deserialize it
      val out = new java.io.ByteArrayOutputStream()
      val oos = new java.io.ObjectOutputStream(out)
      val bytesWritten = shadow.serialize(oos)
      bytesWritten shouldEqual 13
      oos.close()
      val in = new java.io.ByteArrayInputStream(out.toByteArray)
      val ois = new java.io.ObjectInputStream(in)
      val shadow2 = new DeltaShadow()
      shadow2.deserialize(ois)

      // Check that shadow and shadow2 have the same properties
      shadow2.recvCount shouldEqual shadow.recvCount
      shadow2.supervisor shouldEqual shadow.supervisor
      shadow2.interned shouldEqual shadow.interned
      shadow2.isRoot shouldEqual shadow.isRoot
      shadow2.isBusy shouldEqual shadow.isBusy
      shadow2.outgoing shouldEqual shadow.outgoing
    }
  }
}
