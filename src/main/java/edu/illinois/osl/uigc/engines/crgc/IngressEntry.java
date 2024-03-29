package edu.illinois.osl.uigc.engines.crgc;

import akka.actor.Address;
import akka.actor.ActorRef;
import edu.illinois.osl.uigc.engines.crgc.jfr.CountingObjectStream;
import edu.illinois.osl.uigc.engines.crgc.jfr.DeltaGraphSerialization;
import edu.illinois.osl.uigc.engines.crgc.jfr.IngressEntrySerialization;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;

public class IngressEntry implements Serializable {
    HashMap<ActorRef, Field> admitted;
    Address egressAddress, ingressAddress;
    int id;
    int size;
        // This integer counts the number of hashmap entries.
    boolean isFinal;
        // Whether this is the last ingress entry sent by the ingress actor.

    public static class Field implements Serializable {
        int messageCount;
        HashMap<ActorRef, Integer> createdRefs;
            // We could probably be smarter about the data structure based on
            // empirical study.

        public Field() {
            this.messageCount = 0;
            this.createdRefs = new HashMap<>();
        }
    }

    public IngressEntry() {
        this.admitted = new HashMap<>();
        this.size = 0;
        this.isFinal = false;
    }

    public void onMessage(ActorRef recipient, Iterable<Refob<?>> refs) {
        Field field = this.admitted.get(recipient);
        if (field == null) {
            field = new Field();
            this.admitted.put(recipient, field);
            size++;
        }
        // Increase message count.
        field.messageCount += 1;
        // For each ref in the message, add to createdRefs.
        for (Refob<?> refob : refs) {
            ActorRef target = refob.target().classicRef();
            int n = field.createdRefs.getOrDefault(target, 0);
            if (n == 0) size++;
            field.createdRefs.put(target, n + 1);
        }
    }


    // Override the serializer to track the serialized size of the graph.
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        IngressEntrySerialization metrics = new IngressEntrySerialization();
        metrics.begin();

        CountingObjectStream countingStream = new CountingObjectStream(out);
        ObjectOutputStream oos = new ObjectOutputStream(countingStream);
        oos.defaultWriteObject();

        metrics.size = countingStream.getBytesWritten();
        metrics.commit();
    }

}
