package edu.illinois.osl.akka.gc.protocols.monotone;

import akka.actor.Address;
import akka.actor.ActorRef;

import java.io.Serializable;
import java.util.HashMap;

public class IngressEntry implements Serializable {
    HashMap<ActorRef, Field> admitted;
    Address egressAddress, ingressAddress;
    int id;

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
    }
}
