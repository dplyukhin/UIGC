package edu.illinois.osl.akka.gc.protocols.monotone;

import java.util.*;

public class GC {
    /** The size of each array in an entry */
    static int ARRAY_MAX = 4;
    boolean MARKED = false;
    ArrayList<Shadow> from;

    public GC() {
        from = new ArrayList<>();
    }

    public void intern(Refob<?> refob, Shadow shadow) {
        if (!shadow.isInterned) {
            from.add(shadow);
            shadow.self = refob;
                // Shadows must be created by the parent before the parent knows the child's name.
                // We set the `self` field here when we first encounter the shadow.
            shadow.mark = !MARKED;
                // The value of MARKED flips on every GC scan. Make sure this shadow is unmarked.
            shadow.isLocal = false;
                // We haven't seen this shadow before, so we can't have received a snapshot from it.
            shadow.isInterned = true;
        }
    }

    public void processEntry(Entry entry) {
        // Created refs.
        for (int i = 0; i < ARRAY_MAX; i++) {
            if (entry.createdOwners[i] == null) break;
            Refob<?> owner = entry.createdOwners[i];
            Refob<?> target = entry.createdTargets[i];

            // Increment the number of outgoing refs to the target
            Shadow shadow = owner.targetShadow();
            intern(owner, shadow);
            int count = shadow.outgoing.getOrDefault(target, 0);
            shadow.outgoing.put(target, count + 1);
        }

        // Spawned actors.
        for (int i = 0; i < ARRAY_MAX; i++) {
            if (entry.spawnedActors[i] == null) break;
            Refob<?> child = entry.spawnedActors[i];

            // Set the child's supervisor field
            Shadow childShadow = child.targetShadow();
            intern(child, childShadow);
            childShadow.supervisor = entry.self;
            // NB: We don't increase the parent's created count; that info is in the child snapshot.
        }

        // Local information.
        Shadow selfShadow = entry.self.targetShadow();
        intern(entry.self, selfShadow);
        selfShadow.isLocal = true; // Mark it as local now that we have a snapshot from the actor.
        selfShadow.recvCount += entry.recvCount;
        selfShadow.isBusy = entry.isBusy;
        if (entry.becameRoot) {
            selfShadow.isRoot = true;
        }

        // Deactivate refs.
        for (int i = 0; i < ARRAY_MAX; i++) {
            if (entry.updatedRefs[i] == null) break;
            Refob<?> target = entry.updatedRefs[i];
            short info = entry.updatedInfos[i];
            boolean isActive = RefobInfo.isActive(info);
            boolean isDeactivated = !isActive;

            // Update the owner's outgoing references
            if (isDeactivated) {
                int count = selfShadow.outgoing.getOrDefault(target, 0);
                if (count == 1)
                    selfShadow.outgoing.remove(target);
                else
                    selfShadow.outgoing.put(target, count - 1); // may be negative!
            }
        }

        // Update send counts
        for (int i = 0; i < ARRAY_MAX; i++) {
            if (entry.updatedRefs[i] == null) break;
            Refob<?> target = entry.updatedRefs[i];
            short info = entry.updatedInfos[i];
            short sendCount = RefobInfo.count(info);

            // Update the target's receive count
            if (sendCount > 0) {
                Shadow targetShadow = target.targetShadow();
                intern(target, targetShadow);
                targetShadow.recvCount -= sendCount; // may be negative!
            }
        }
    }

    private static boolean isUnblocked(Shadow shadow) {
        return shadow.isBusy || shadow.recvCount != 0;
    }
    private static boolean isExternal(Shadow shadow) {
        return !shadow.isLocal;
    }

    public int trace() {
        System.out.println("Scanning " + from.size() + " actors...");
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
            // Mark the outgoing references
            for (Refob<?> targetRefob : owner.outgoing.keySet()) {
                Shadow target = targetRefob.targetShadow();
                if (target.mark != MARKED) {
                    to.add(target);
                    target.mark = MARKED;
                }
            }
            // Mark the actors that are monitoring or supervising this one
            if (owner.supervisor != null) {
                Shadow supervisor = owner.supervisor.targetShadow();
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
                shadow.self.target().unsafeUpcast().$bang(new StopMsg());
            }
        }
        from = to;
        return count;
    }
}
