package edu.illinois.osl.uigc.engines.crgc

import akka.actor.Address
import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import org.scalatest.wordspec.AnyWordSpecLike
import scala.jdk.CollectionConverters._

class SerializationSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {

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

  "Delta Graphs" must {

    val address: Address = system.address

    "serialize and deserialize correctly - empty graphs" in {
      val graph = DeltaGraph.initialize(address)
      testKit.serializationTestKit.verifySerialization(graph)
    }

    "serialize and deserialize correctly - two-actor graph" in {
      val ref1: TestProbe[GCMessage[Nothing]] = testKit.createTestProbe()
      val ref2: TestProbe[GCMessage[Nothing]] = testKit.createTestProbe()
      val refob1: Refob[Nothing] = new Refob[Nothing](ref1.ref, null)
      val refob2: Refob[Nothing] = new Refob[Nothing](ref2.ref, null)
      val state1 = new State(refob1)

      state1.recordNewActor(refob2)
      refob2.incSendCount()
      state1.recordUpdatedRefob(refob2)
      val entry = new Entry()
      state1.flushToEntry(false, entry)

      val graph = DeltaGraph.initialize(address)
      graph.mergeEntry(entry)
      graph.size shouldEqual 2

      testKit.serializationTestKit.verifySerialization(graph)
    }

  }

  "Ingress Entries" must {

    "serialize and deserialize correctly - empty entries" in {
      val entry = new IngressEntry()
      entry.egressAddress = system.address
      entry.ingressAddress = system.address
      testKit.serializationTestKit.verifySerialization(entry)
    }

    "serialize and deserialize correctly - non-empty entries" in {
      val ref1: TestProbe[GCMessage[Nothing]] = testKit.createTestProbe()
      val ref2: TestProbe[GCMessage[Nothing]] = testKit.createTestProbe()
      val refob1: Refob[Nothing] = new Refob[Nothing](ref1.ref, null)
      val refob2: Refob[Nothing] = new Refob[Nothing](ref2.ref, null)

      val entry = new IngressEntry()
      entry.egressAddress = system.address
      entry.ingressAddress = system.address
      entry.onMessage(ref1.ref.classicRef, Nil.asJava)
      entry.onMessage(ref2.ref.classicRef, (refob1 :: Nil).asJava)
      testKit.serializationTestKit.verifySerialization(entry)
    }

  }
}
