package edu.illinois.osl.uigc.engines.crgc;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;

/**
 * An object summarizing a batch of {@link Entry}s from a single actor. ActorRefs in the DeltaShadow
 * are encoded as short integers to save space; use the enclosing {@link DeltaGraph} to map the
 * short integer back to an ActorRef.
 */
public class DeltaShadow implements Serializable {
    //@JsonDeserialize(keyUsing = OutgoingDeserializer.class)
    HashMap<Short, Integer> outgoing;
    int recvCount;
    short supervisor;
    boolean isRoot;
    boolean isBusy;
    boolean interned;
        // This field will be set to `true` if any of the entries in this batch were
        // produced by this actor.

    public DeltaShadow() {
        this.outgoing = new HashMap<>();
        this.supervisor = -1; // Set to an invalid value if it didn't change
        this.recvCount = 0;
        this.isRoot = false;
        this.isBusy = false;
        this.interned = false;
    }

    public static class OutgoingDeserializer extends KeyDeserializer {

        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
            return Short.parseShort(key);
        }
    }
}
