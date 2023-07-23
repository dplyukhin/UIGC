package edu.illinois.osl.akka.gc.protocols.monotone;

import akka.actor.typed.ActorRef;

import java.util.*;

public class ShadowGraph {
    /** The size of each array in an entry */
    boolean MARKED = true;
    ArrayList<Shadow> from;
    HashMap<ActorRef<?>, Shadow> shadowMap;

    public ShadowGraph() {
        from = new ArrayList<>();
        shadowMap = new HashMap<>();
    }

    public Shadow getShadow(Refob<?> refob) {
        // Check if it's in the cache.
        if (refob.targetShadow() != null)
            return refob.targetShadow();

        // Try to get it from the collection of all my shadows. Save it in the cache.
        Shadow shadow = getShadow(refob.target());
        refob.targetShadow_$eq(shadow);

        return shadow;
    }

    public Shadow getShadow(ActorRef<?> ref) {
        // Try to get it from the collection of all my shadows.
        Shadow shadow = shadowMap.get(ref);
        if (shadow != null)
            return shadow;

        // Haven't heard of this actor yet. Create a shadow for it.
        return makeShadow(ref);
    }

    public Shadow makeShadow(ActorRef<?> ref) {
        // Haven't heard of this actor yet. Create a shadow for it.
        Shadow shadow = new Shadow();
        shadow.self = ref;
        shadow.mark = !MARKED;
            // The value of MARKED flips on every GC scan. Make sure this shadow is unmarked.
        shadow.interned = false;
            // We haven't seen this shadow before, so we can't have received a snapshot from it.

        shadowMap.put(ref, shadow);
        from.add(shadow);
        return shadow;
    }

    public void updateOutgoing(Map<Shadow, Integer> outgoing, Shadow target, int delta) {
        int count = outgoing.getOrDefault(target, 0);
        if (count + delta == 0) {
            // Instead of writing zero, we delete the count.
            outgoing.remove(target);
        }
        else {
            outgoing.put(target, count + delta);
        }
    }

    public void mergeEntry(Entry entry) {
        // Local information.
        Shadow selfShadow = getShadow(entry.self);
        selfShadow.interned = true; // We now have a snapshot from the actor.
        selfShadow.isLocal = true;  // Entries only come from actors on this node.
        selfShadow.recvCount += entry.recvCount;
        selfShadow.isBusy = entry.isBusy;
        selfShadow.isRoot = entry.isRoot;

        // Created refs.
        for (int i = 0; i < Sizes.EntryFieldSize; i++) {
            if (entry.createdOwners[i] == null) break;
            Refob<?> owner = entry.createdOwners[i];
            Shadow targetShadow = getShadow(entry.createdTargets[i]);

            // Increment the number of outgoing refs to the target
            Shadow shadow = getShadow(owner);
            updateOutgoing(shadow.outgoing, targetShadow, 1);
        }

        // Spawned actors.
        for (int i = 0; i < Sizes.EntryFieldSize; i++) {
            if (entry.spawnedActors[i] == null) break;
            Refob<?> child = entry.spawnedActors[i];

            // Set the child's supervisor field
            Shadow childShadow = getShadow(child);
            childShadow.supervisor = selfShadow;
            // NB: We don't increase the parent's created count; that info is in the child snapshot.
        }

        // Deactivate refs.
        for (int i = 0; i < Sizes.EntryFieldSize; i++) {
            if (entry.updatedRefs[i] == null) break;
            Shadow targetShadow = getShadow(entry.updatedRefs[i]);
            short info = entry.updatedInfos[i];
            boolean isActive = RefobInfo.isActive(info);
            boolean isDeactivated = !isActive;

            // Update the owner's outgoing references
            if (isDeactivated) {
                updateOutgoing(selfShadow.outgoing, targetShadow, -1);
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
                Shadow targetShadow = getShadow(target);
                targetShadow.recvCount -= sendCount; // may be negative!
            }
        }
    }

    public void mergeDelta(DeltaGraph delta) {
        // This will act as a hashmap, mapping compressed IDs to actorRefs.
        ActorRef<?>[] refs = new ActorRef<?>[delta.currentSize];
        for (Map.Entry<ActorRef<?>, Short> entry : delta.compressionTable.entrySet()) {
            refs[entry.getValue()] = entry.getKey();
        }

        for (short i = 0; i < delta.currentSize; i++) {
            DeltaShadow deltaShadow = delta.shadows[i];
            Shadow shadow = getShadow(refs[i]);

            shadow.interned = shadow.interned || deltaShadow.isLocal;
                // Set `interned` if we have already received an entry from this actor
                // or if an entry was received in the latest batch.
            shadow.isLocal = false;
                // Delta graphs only come from remote actors.
            shadow.recvCount += deltaShadow.recvCount;
            shadow.isBusy = deltaShadow.isBusy;
            shadow.isRoot = deltaShadow.isRoot;
            if (deltaShadow.supervisor >= 0) {
                shadow.supervisor = getShadow(refs[deltaShadow.supervisor]);
            }
            for (Map.Entry<Short, Integer> entry : deltaShadow.outgoing.entrySet()) {
                short id = entry.getKey();
                int count = entry.getValue();
                updateOutgoing(shadow.outgoing, getShadow(refs[id]), count);
            }
        }
    }

    public void assertEquals(ShadowGraph that) {
        assert (this.shadowMap.keySet().equals(that.shadowMap.keySet()))
                : "Shadow maps have different actors:\n"
                + "This: " + this.shadowMap.keySet() + "\n"
                + "That: " + that.shadowMap.keySet();

        for (Map.Entry<ActorRef<?>, Shadow> entry : this.shadowMap.entrySet()) {
            Shadow thisShadow = entry.getValue();
            Shadow thatShadow = that.shadowMap.get(entry.getKey());
            thisShadow.assertEquals(thatShadow);
        }
    }

    private static boolean isUnblocked(Shadow shadow) {
        return shadow.isRoot || shadow.isBusy || shadow.recvCount != 0;
    }

    public int trace() {
        //System.out.println("Scanning " + from.size() + " actors...");
        ArrayList<Shadow> to = new ArrayList<>();
        // 0. Assume all shadows in `from` are in the UNMARKED state.
        //    Also assume that, if an actor has an incoming external actor, that external has a snapshot in `from`.
        // 1. Find all the shadows that are (a) internal and unblocked, or (b) external --- and mark them and move them to `to`.
        // 2. Trace a path from every marked shadow, moving marked shadows to `to`.
        // 3. Find all unmarked shadows in `from` and kill those actors.
        // 4. The `to` set becomes the new `from` set.
        for (Shadow shadow : from) {
            if (isUnblocked(shadow) || !shadow.interned) {
                to.add(shadow);
                shadow.mark = MARKED;
            }
        }
        for (int scanptr = 0; scanptr < to.size(); scanptr++) {
            Shadow owner = to.get(scanptr);
            // Mark the outgoing references whose count is greater than zero
            for (Map.Entry<Shadow, Integer> entry : owner.outgoing.entrySet()) {
                Shadow target = entry.getKey();
                if (entry.getValue() > 0 && target.mark != MARKED) {
                    to.add(target);
                    target.mark = MARKED;
                }
            }
            // Mark the actors that are monitoring or supervising this one
            Shadow supervisor = owner.supervisor;
            if (supervisor != null) {
                if (supervisor.mark != MARKED) {
                    to.add(supervisor);
                    supervisor.mark = MARKED;
                }
            }
        }

        // Unmarked actors are garbage. Due to supervision, an actor will only be garbage if all its descendants
        // are also garbage.
        int count = 0;
        for (Shadow shadow : from) {
            if (shadow.mark != MARKED && shadow.isLocal) {
                count++;
                shadow.self.unsafeUpcast().tell(StopMsg$.MODULE$);
                shadowMap.remove(shadow.self);
            }
        }
        from = to;
        MARKED = !MARKED;
        return count;
    }

    public void startWave() {
        int count = 0;
        for (Shadow shadow : from) {
            if (shadow.isRoot) {
                count++;
                shadow.self.unsafeUpcast().tell(WaveMsg$.MODULE$);
            }
        }
    }
}
