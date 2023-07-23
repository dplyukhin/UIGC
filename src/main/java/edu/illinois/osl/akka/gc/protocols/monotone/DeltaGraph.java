package edu.illinois.osl.akka.gc.protocols.monotone;

import edu.illinois.osl.akka.gc.interfaces.RefLike;

import java.util.HashMap;

/**
 * A compact, serializable summary of a batch of entries. Because ActorRefs are so large,
 * the graph contains a compression table mapping ActorRefs to short integers.
 * <p>
 * Nodes in the graph are called DeltaShadows. They are stored in an array of shadows.
 * The location of an actor's delta-shadow in the array is equal to its compressed actor name.
 */
public class DeltaGraph {

    HashMap<RefLike<?>, Short> compressionTable;
    DeltaShadow[] shadows;
    int graphID;
    short currentSize;

    public static class DeltaShadow {
        HashMap<Short, Integer> outgoing;
        short supervisor;
        int recvCount;
        boolean isRoot;
        boolean isBusy;
        boolean isLocal;
            // This field will be set to `true` if any of the entries in this batch were
            // produced by this actor.

        public DeltaShadow() {
            this.outgoing = new HashMap<>();
            this.supervisor = -1; // Set to an invalid value if it didn't change
            this.recvCount = 0;
            this.isRoot = false;
            this.isBusy = false;
            this.isLocal = false;
        }
    }

    public DeltaGraph(int graphID) {
        this.compressionTable = new HashMap<>(Sizes.DeltaGraphSize);
        this.shadows = new DeltaShadow[Sizes.DeltaGraphSize];
        this.graphID = graphID;
        this.currentSize = 0;
    }

    /**
     * Merge the given entry into the delta graph.
     * @return whether the graph is full.
     */
    public boolean mergeEntry(Entry entry) {
        // Local information.
        short selfID = getID(entry.self);
        DeltaShadow selfShadow = shadows[selfID];
        selfShadow.recvCount += entry.recvCount;
        selfShadow.isBusy = entry.isBusy;
        selfShadow.isRoot = entry.isRoot;
        selfShadow.isLocal = true;

        // Created refs.
        for (int i = 0; i < Sizes.EntryFieldSize; i++) {
            if (entry.createdOwners[i] == null) break;
            Refob<?> owner = entry.createdOwners[i];
            short targetID = getID(entry.createdTargets[i]);

            // Increment the number of outgoing refs to the target
            short ownerID = getID(owner.target());
            DeltaShadow ownerShadow = shadows[ownerID];
            int count = ownerShadow.outgoing.getOrDefault(targetID, 0);
            if (count == -1) {
                // Instead of writing zero, we delete the count.
                ownerShadow.outgoing.remove(targetID);
            }
            else {
                ownerShadow.outgoing.put(targetID, count + 1);
            }
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
                int count = selfShadow.outgoing.getOrDefault(targetID, 0);
                if (count == 1)
                    selfShadow.outgoing.remove(targetID);
                else
                    selfShadow.outgoing.put(targetID, count - 1); // may be negative!
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

        /* Sleazy hack to avoid overflows: We know that merging an entry can only produce
         * so many new shadows. So we never fill the delta graph to actual capacity; we
         * tell the GC to finalize the delta graph if the next entry *could potentially*
         * cause an overflow. */
        return currentSize + (4 * Sizes.EntryFieldSize) + 1 >= Sizes.DeltaGraphSize;
    }

    private short getID(Refob<?> refob) {
        return getID(refob.target());
    }

    private short getID(RefLike<?> ref) {
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

}
