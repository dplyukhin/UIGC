package edu.illinois.osl.uigc.engines.crgc;

import akka.actor.Address;
import akka.actor.ActorRef;
import akka.serialization.jackson.ActorRefDeserializer;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * A compact, serializable summary of a batch of entries. Because ActorRefs are so large,
 * the graph contains a compression table mapping ActorRefs to short integers.
 * <p>
 * Nodes in the graph are called DeltaShadows. They are stored in an array of shadows.
 * The location of an actor's delta-shadow in the array is equal to its compressed actor name.
 */
public class DeltaGraph implements Serializable {

    //@JsonDeserialize(using = AkkaSerializationDeserializer.class)
    //@JsonSerialize(using = AkkaSerializationSerializer.class)
    HashMap<ActorRef, Short> compressionTable;
    DeltaShadow[] shadows;
    Address address;
    //ArrayList<Entry> entries;
    int numEntriesMerged;
    short currentSize;


    public DeltaGraph() {
        this.compressionTable = new HashMap<>(Sizes.DeltaGraphSize);
        this.shadows = new DeltaShadow[Sizes.DeltaGraphSize];
        //this.entries = new ArrayList<>();
        this.numEntriesMerged = 0;
        this.currentSize = 0;
    }

    /** This is a separate constructor because the serializer doesn't like non-empty constructors. */
    public void initialize(Address address) {
        this.address = address;
    }

    public void updateOutgoing(Map<Short, Integer> outgoing, Short target, int delta) {
        int count = outgoing.getOrDefault(target, 0);
        if (count + delta == 0) {
            // Instead of writing zero, we delete the count.
            outgoing.remove(target);
        }
        else {
            outgoing.put(target, count + delta);
        }
    }

    /**
     * Merge the given entry into the delta graph.
     * @return whether the graph is full.
     */
    public boolean mergeEntry(Entry entry) {
        // Local information.
        short selfID = getID(entry.self);
        DeltaShadow selfShadow = shadows[selfID];
        selfShadow.interned = true;
        selfShadow.recvCount += entry.recvCount;
        selfShadow.isBusy = entry.isBusy;
        selfShadow.isRoot = entry.isRoot;

        // Created refs.
        for (int i = 0; i < Sizes.EntryFieldSize; i++) {
            if (entry.createdOwners[i] == null) break;
            Refob<?> owner = entry.createdOwners[i];
            short targetID = getID(entry.createdTargets[i]);

            // Increment the number of outgoing refs to the target
            short ownerID = getID(owner);
            DeltaShadow ownerShadow = shadows[ownerID];
            updateOutgoing(ownerShadow.outgoing, targetID, 1);
        }

        // Spawned actors.
        for (int i = 0; i < Sizes.EntryFieldSize; i++) {
            if (entry.spawnedActors[i] == null) break;
            Refob<?> child = entry.spawnedActors[i];

            // Set the child's supervisor field
            short childID = getID(child);
            DeltaShadow childShadow = shadows[childID];
            childShadow.supervisor = selfID;
            // NB: We don't increase the parent's created count; that info is in the child snapshot.
        }

        // Deactivate refs.
        for (int i = 0; i < Sizes.EntryFieldSize; i++) {
            if (entry.updatedRefs[i] == null) break;
            short targetID = getID(entry.updatedRefs[i]);
            short info = entry.updatedInfos[i];
            boolean isActive = RefobInfo.isActive(info);
            boolean isDeactivated = !isActive;

            // Update the owner's outgoing references
            if (isDeactivated) {
                updateOutgoing(selfShadow.outgoing, targetID, -1);
            }
        }

        // Update send counts
        for (int i = 0; i < Sizes.EntryFieldSize; i++) {
            if (entry.updatedRefs[i] == null) break;
            Refob<?> target = entry.updatedRefs[i];
            short info = entry.updatedInfos[i];
            short sendCount = RefobInfo.count(info);

            // Update the target's receive count
            if (sendCount > 0) {
                short targetID = getID(target);
                DeltaShadow targetShadow = shadows[targetID];
                targetShadow.recvCount -= sendCount; // may be negative!
            }
        }

        numEntriesMerged++;

        /* Sleazy hack to avoid overflows: We know that merging an entry can only produce
         * so many new shadows. So we never fill the delta graph to actual capacity; we
         * tell the GC to finalize the delta graph if the next entry *could potentially*
         * cause an overflow. */
        return currentSize + (4 * Sizes.EntryFieldSize) + 1 >= Sizes.DeltaGraphSize;
    }

    private short getID(Refob<?> refob) {
        return getID(refob.target().classicRef());
    }

    private short getID(ActorRef ref) {
        if (compressionTable.containsKey(ref))
            return compressionTable.get(ref);

        short id = currentSize++;
        compressionTable.put(ref, id);
        shadows[id] = new DeltaShadow();
        return id;
    }

    public boolean nonEmpty() {
        return currentSize > 0;
    }

    public static class CompressionDeserializer extends KeyDeserializer {

        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
            return ActorRefDeserializer.instance().deserialize(ctxt.getParser(), ctxt);
        }
    }
}
