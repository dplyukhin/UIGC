package edu.illinois.osl.akka.gc.protocols.monotone;

import akka.actor.Address;
import akka.actor.ActorRef;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;

public class IngressEntry implements Serializable {
    HashMap<ActorRef, Field> admitted;
    Address egressAddress, ingressAddress;
    int id;
    int size;
        // This integer counts the number of hashmap entries.

    public static class Field {
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
    }

    public void onMessage(ActorRef recipient, Iterable<Refob<?>> refs) {
        var field = this.admitted.get(recipient);
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
}
