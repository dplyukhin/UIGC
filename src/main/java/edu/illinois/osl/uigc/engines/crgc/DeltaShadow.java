package edu.illinois.osl.uigc.engines.crgc;

import java.io.*;
import java.util.HashMap;

/**
 * An object summarizing a batch of {@link Entry}s from a single actor. ActorRefs in the DeltaShadow
 * are encoded as short integers to save space; use the enclosing {@link DeltaGraph} to map the
 * short integer back to an ActorRef.
 */
public class DeltaShadow implements Serializable {
    //@JsonDeserialize(keyUsing = OutgoingDeserializer.class)

    /**
     * A mapping from actors to net reference counts. If the value of outgoing(b) is positive, it
     * means this actor has gained that many references to b. If the value is negative, it means this
     * actor has deactivated that many references to b.
     */
    HashMap<Short, Integer> outgoing;
    /**
     * The net number of messages this actor has received, according to the latest batch of entries.
     * May be negative, meaning this actor has received fewer messages than have been sent to it.
     */
    int recvCount;
    /**
     * The compressed ID of the actor supervising this one. Defaults to (-1) if the supervisor is unknown.
     */
    short supervisor;
    /**
     * This field is true iff any of the entries in this batch were produced by this actor.
     */
    boolean interned;
    /**
     * If {@link DeltaShadow#interned} is true, this indicates whether the actor was a root in its latest entry.
     * (If {@link DeltaShadow#interned} is false, this field is meaningless.)
     */
    boolean isRoot;
    /**
     * If {@link DeltaShadow#interned} is true, this indicates whether the actor was busy in its latest entry.
     * (If {@link DeltaShadow#interned} is false, this field is meaningless.)
     */
    boolean isBusy;

    public DeltaShadow() {
        this.outgoing = new HashMap<>();
        this.supervisor = -1; // Set to an invalid value if it didn't change
        this.recvCount = 0;
        this.isRoot = false;
        this.isBusy = false;
        this.interned = false;
    }

    /**
     * Serialize the shadow to the output stream.
     * @return the number of bytes written
     */
    public int serialize(ObjectOutputStream out) throws IOException {
        out.writeInt(recvCount);
        out.writeShort(supervisor);
        out.writeBoolean(interned);
        out.writeBoolean(isRoot);
        out.writeBoolean(isBusy);
        out.writeInt(outgoing.size());
        for (var entry : outgoing.entrySet()) {
            out.writeShort(entry.getKey());
            out.writeInt(entry.getValue());
        }
        return 4 + 2 + 1 + 1 + 1 + 4 + outgoing.size() * (2 + 4);
    }

    public void deserialize(ObjectInputStream in) throws IOException {
        recvCount = in.readInt();
        supervisor = in.readShort();
        interned = in.readBoolean();
        isRoot = in.readBoolean();
        isBusy = in.readBoolean();
        int size = in.readInt();
        outgoing = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            short key = in.readShort();
            int value = in.readInt();
            outgoing.put(key, value);
        }
    }
}
