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
 * A compact, serializable way to summarize a collection of {@link Entry}s produced by a particular actor system.
 * Initially, a delta graph is empty. Merging an entry into the graph may add {@link DeltaShadow}s to the graph;
 * each delta shadow represents an actor referenced by one of the entries.
 * <p>
 * To reduce bandwidth, the graph encodes ActorRefs with a compressed ID (a short integer).
 * The {@link DeltaGraph#decoder} method produces an array for mapping compressed IDs back to ActorRefs.
 * <p>
 * Delta shadows are stored consecutively in the {@link DeltaGraph#shadows} array. Their index in the array
 * is the same as their compressed ID.
 */
public class DeltaGraph implements Serializable {

    //@JsonDeserialize(using = AkkaSerializationDeserializer.class)
    //@JsonSerialize(using = AkkaSerializationSerializer.class)
    /**
     * The compression table that maps ActorRefs to compressed IDs.
     */
    private final HashMap<ActorRef, Short> compressionTable;
    /**
     * Delta shadows are stored in this array. An actor's compressed ID is its position in the array.
     */
    final DeltaShadow[] shadows;
    /**
     * The address of the node that produced this graph.
     */
    Address address;
    /**
     * The number of entries that have been merged into this graph.
     */
    int numEntriesMerged;
    /**
     * The number of delta shadows in this graph.
     */
    short size;

    /**
     * FOR INTERNAL USE ONLY! The serializer wants a public empty constructor.
     * Use {@link DeltaGraph#initialize} instead.
     *
     * @deprecated
     */
    public DeltaGraph() {
        this.compressionTable = new HashMap<>(Sizes.DeltaGraphSize);
        this.shadows = new DeltaShadow[Sizes.DeltaGraphSize];
        this.numEntriesMerged = 0;
        this.size = 0;
    }

    /**
     * The main constructor for delta graphs.
     *
     * @param address the address of the ActorSystem that created this graph
     */
    public static DeltaGraph initialize(Address address) {
        DeltaGraph graph = new DeltaGraph();
        graph.address = address;
        return graph;
    }

    /**
     * Merges the given entry into the delta graph. Assumes the graph is not full.
     */
    public void mergeEntry(Entry entry) {
        // Local information.
        short selfID = encode(entry.self);
        DeltaShadow selfShadow = shadows[selfID];
        selfShadow.interned = true;
        selfShadow.recvCount += entry.recvCount;
        selfShadow.isBusy = entry.isBusy;
        selfShadow.isRoot = entry.isRoot;

        // Created refs.
        for (int i = 0; i < Sizes.EntryFieldSize; i++) {
            if (entry.createdOwners[i] == null) break;
            Refob<?> owner = entry.createdOwners[i];
            short targetID = encode(entry.createdTargets[i]);

            // Increment the number of outgoing refs to the target
            short ownerID = encode(owner);
            DeltaShadow ownerShadow = shadows[ownerID];
            updateOutgoing(ownerShadow.outgoing, targetID, 1);
        }

        // Spawned actors.
        for (int i = 0; i < Sizes.EntryFieldSize; i++) {
            if (entry.spawnedActors[i] == null) break;
            Refob<?> child = entry.spawnedActors[i];

            // Set the child's supervisor field
            short childID = encode(child);
            DeltaShadow childShadow = shadows[childID];
            childShadow.supervisor = selfID;
            // NB: We don't increase the parent's created count; that info is in the child snapshot.
        }

        // Deactivate refs.
        for (int i = 0; i < Sizes.EntryFieldSize; i++) {
            if (entry.updatedRefs[i] == null) break;
            short targetID = encode(entry.updatedRefs[i]);
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
                short targetID = encode(target);
                DeltaShadow targetShadow = shadows[targetID];
                targetShadow.recvCount -= sendCount; // may be negative!
            }
        }

        numEntriesMerged++;
    }

    private void updateOutgoing(Map<Short, Integer> outgoing, Short target, int delta) {
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
     * Returns the compressed ID of a reference, possibly allocating a new {@link DeltaShadow} in the process.
     */
    private short encode(Refob<?> refob) {
        return encode(refob.target().classicRef());
    }

    /**
     * Returns the compressed ID of a reference, possibly allocating a new {@link DeltaShadow} in the process.
     */
    private short encode(ActorRef ref) {
        if (compressionTable.containsKey(ref))
            return compressionTable.get(ref);

        short id = size++;
        compressionTable.put(ref, id);
        shadows[id] = new DeltaShadow();
        return id;
    }

    /**
     * Returns an array that maps compressed IDs to ActorRefs. This is used to decode the compressed IDs
     * used in {@link DeltaShadow}.
     */
    public ActorRef[] decoder() {
        // This will act as a hashmap, mapping compressed IDs to actorRefs.
        ActorRef[] refs = new ActorRef[this.size];
        for (Map.Entry<ActorRef, Short> entry : this.compressionTable.entrySet()) {
            refs[entry.getValue()] = entry.getKey();
        }
        return refs;
    }

    /**
     * Whether the graph is full, i.e. merging new entries can cause an error.
     */
    public boolean isFull() {
        /* Sleazy hack to avoid overflows: We know that merging an entry can only produce
         * so many new shadows. So we never fill the delta graph to actual capacity; we
         * tell the GC to finalize the delta graph if the next entry *could potentially*
         * cause an overflow. */
        return size + (4 * Sizes.EntryFieldSize) + 1 >= Sizes.DeltaGraphSize;
    }

    /**
     * Whether the graph is nonempty, i.e. there is at least one {@link DeltaShadow} in the graph.
     */
    public boolean nonEmpty() {
        return size > 0;
    }

    public static class CompressionDeserializer extends KeyDeserializer {

        @Override
        public Object deserializeKey(String key, DeserializationContext ctxt) throws IOException {
            return ActorRefDeserializer.instance().deserialize(ctxt.getParser(), ctxt);
        }
    }
}
