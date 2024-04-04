package edu.illinois.osl.uigc.engines.crgc;

import akka.actor.Address;
import akka.actor.ActorRef;
import edu.illinois.osl.uigc.engines.crgc.jfr.IngressEntrySerialization;

import java.io.*;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

public class IngressEntry implements Serializable {
    /**
     * Sequence number, identifying the number of ingress entries sent by the ingress actor.
     */
    int id;
    /**
     * For each actor A, this field maps A to a record, indicating:
     * 1. The number of messages sent to A.
     * 2. For each actor B, the number of references admitted by this ingress actor, owned by A and pointing to B.
     */
    HashMap<ActorRef, Field> admitted;
    /**
     * Addresses of this ingress actor and the egress actor.
     */
    Address egressAddress, ingressAddress;
    /**
     * Whether this is the last ingress entry sent by the ingress actor.
     */
    boolean isFinal;

    public static class Field implements Serializable {
        int messageCount;
        HashMap<ActorRef, Integer> createdRefs;
            // We could probably be smarter about the data structure based on
            // empirical study.

        public Field() {
            this.messageCount = 0;
            this.createdRefs = new HashMap<>();
        }

        public int serialize(ObjectOutputStream out) throws IOException {
            int bytesWritten = 0;

            out.writeInt(this.messageCount);
            bytesWritten += 4;

            out.writeInt(this.createdRefs.size());
            for (ActorRef ref : this.createdRefs.keySet()) {
                out.writeObject(ref);
                out.writeInt(this.createdRefs.get(ref));
                bytesWritten += 4 + ref.toString().length();
            }

            return bytesWritten;
        }

        public void deserialize(ObjectInputStream in) throws IOException, ClassNotFoundException {
            this.messageCount = in.readInt();

            int numRefs = in.readInt();
            this.createdRefs = new HashMap<>(numRefs);
            for (int i = 0; i < numRefs; i++) {
                ActorRef ref = (ActorRef) in.readObject();
                int count = in.readInt();
                this.createdRefs.put(ref, count);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;

            Field other = (Field) obj;
            return this.messageCount == other.messageCount
                && this.createdRefs.equals(other.createdRefs);
        }
    }

    public IngressEntry() {
        this.admitted = new HashMap<>();
        this.isFinal = false;
    }

    // Allocate the closures here, so we don't do it every time onMessage gets called.
    private static final Function<ActorRef, Field> createField = k -> new Field();
    private static final BiFunction<ActorRef, Integer, Integer> incRefCount = (k, v) -> v == null ? 1 : v + 1;

    public void onMessage(ActorRef recipient, Iterable<Refob<?>> refs) {
        Field field = this.admitted.computeIfAbsent(recipient, createField);
        // Increase message count.
        field.messageCount += 1;
        // For each ref in the message, add to createdRefs.
        for (Refob<?> refob : refs) {
            ActorRef target = refob.target().classicRef();
            field.createdRefs.compute(target, incRefCount);
        }
    }

    // Override the serializer to track the serialized size of the graph.
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        IngressEntrySerialization metrics = new IngressEntrySerialization();
        metrics.begin();

        out.writeInt(this.id);
        out.writeBoolean(this.isFinal);
        out.writeObject(this.ingressAddress);
        out.writeObject(this.egressAddress);

        metrics.size += 4 + 1
            + 2 + ingressAddress.toString().length()
            + 2 + egressAddress.toString().length();

        out.writeInt(this.admitted.size());
        metrics.size += 4;
        for (ActorRef actor : this.admitted.keySet()) {
            out.writeObject(actor);
            int bytesWritten = this.admitted.get(actor).serialize(out);
            metrics.size += 2 + actor.toString().length();
            metrics.size += bytesWritten;
        }

        metrics.commit();
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        this.id = in.readInt();
        this.isFinal = in.readBoolean();
        this.ingressAddress = (Address) in.readObject();
        this.egressAddress = (Address) in.readObject();

        int numActors = in.readInt();
        this.admitted = new HashMap<>(numActors);
        for (int i = 0; i < numActors; i++) {
            ActorRef actor = (ActorRef) in.readObject();
            Field field = new Field();
            field.deserialize(in);
            this.admitted.put(actor, field);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        IngressEntry other = (IngressEntry) obj;
        return this.id == other.id
            && this.isFinal == other.isFinal
            && this.ingressAddress.equals(other.ingressAddress)
            && this.egressAddress.equals(other.egressAddress)
            && this.admitted.equals(other.admitted);
    }
}
