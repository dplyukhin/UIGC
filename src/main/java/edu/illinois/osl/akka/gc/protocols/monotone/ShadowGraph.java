package edu.illinois.osl.akka.gc.protocols.monotone;

import edu.illinois.osl.akka.gc.interfaces.RefLike;

import java.util.*;

public class ShadowGraph {
    /** The size of each array in an entry */
    boolean MARKED = true;
    ArrayList<Shadow> from;
    HashMap<RefLike<?>, Shadow> shadowMap;

    public ShadowGraph() {
        from = new ArrayList<>();
        shadowMap = new HashMap<>();
    }

    public Shadow getShadow(Refob<?> refob) {
        // Check if it's in the cache.
        if (refob.targetShadow() != null)
            return refob.targetShadow();

        // Try to get it from the collection of all my shadows. Save it in the cache.
        Shadow shadow = shadowMap.get(refob.target());
        refob.targetShadow_$eq(shadow);
        if (shadow != null)
            return shadow;

        // Haven't heard of this actor yet. Create a shadow for it.
        shadow = new Shadow();
        shadow.self = refob.target();
        shadow.mark = !MARKED;
            // The value of MARKED flips on every GC scan. Make sure this shadow is unmarked.
        shadow.isLocal = false;
            // We haven't seen this shadow before, so we can't have received a snapshot from it.

        shadowMap.put(refob.target(), shadow);
        from.add(shadow);
        refob.targetShadow_$eq(shadow);

        return shadow;
    }

    public void mergeEntry(Entry entry) {
        // Local information.
        Shadow selfShadow = getShadow(entry.self);
        selfShadow.isLocal = true; // Mark it as local now that we have a snapshot from the actor.
        selfShadow.recvCount += entry.recvCount;
        selfShadow.isBusy = entry.isBusy;
        if (entry.becameRoot) {
            selfShadow.isRoot = true;
        }

        // Created refs.
        for (int i = 0; i < Sizes.EntryFieldSize; i++) {
            if (entry.createdOwners[i] == null) break;
            Refob<?> owner = entry.createdOwners[i];
            Shadow targetShadow = getShadow(entry.createdTargets[i]);

            // Increment the number of outgoing refs to the target
            Shadow shadow = getShadow(owner);
            int count = shadow.outgoing.getOrDefault(targetShadow, 0);
            if (count == -1) {
                // Instead of writing zero, we delete the count.
                shadow.outgoing.remove(targetShadow);
            }
            else {
                shadow.outgoing.put(targetShadow, count + 1);
            }
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
                int count = selfShadow.outgoing.getOrDefault(targetShadow, 0);
                if (count == 1)
                    selfShadow.outgoing.remove(targetShadow);
                else
                    selfShadow.outgoing.put(targetShadow, count - 1); // may be negative!
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

    private static boolean isUnblocked(Shadow shadow) {
        return shadow.isRoot || shadow.isBusy || shadow.recvCount != 0;
    }
    private static boolean isExternal(Shadow shadow) {
        return !shadow.isLocal;
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
            if (isUnblocked(shadow) || isExternal(shadow)) {
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
            if (shadow.mark != MARKED) {
                count++;
                shadow.self.unsafeUpcast().$bang(StopMsg$.MODULE$);
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
                shadow.self.unsafeUpcast().$bang(WaveMsg$.MODULE$);
            }
        }
    }
}
