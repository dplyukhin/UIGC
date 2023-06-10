package edu.illinois.osl.akka.gc.protocols.monotone;

import edu.illinois.osl.akka.gc.interfaces.RefLike;

import java.util.*;

public class GC {
    /** The size of each array in an entry */
    static int ARRAY_MAX = 16; // Need to use a power of 2 for the receive count

    /** Fetch the actor's shadow. If it doesn't exist, create one and mark it as external. */
    private static Shadow getShadow(Map<RefLike<?>, Shadow> shadows, RefLike<?> actor) {
        Shadow s = shadows.get(actor);
        if (s == null) {
            s = new Shadow(actor, false);
            shadows.put(actor, s);
        }
        return s;
    }

    public static void processEntry(Map<RefLike<?>, Shadow> shadows, Entry entry) {
        // Created refs.
        for (int i = 0; i < ARRAY_MAX; i++) {
            if (entry.createdOwners[i] == null) break;
            RefLike<?> owner = entry.createdOwners[i];
            RefLike<?> target = entry.createdTargets[i];

            // Increment the number of outgoing refs to the target
            Shadow shadow = getShadow(shadows, owner);
            int count = shadow.outgoing.getOrDefault(target, 0);
            shadow.outgoing.put(target, count + 1);
        }

        // Spawned actors.
        for (int i = 0; i < ARRAY_MAX; i++) {
            if (entry.spawnedActors[i] == null) break;
            RefLike<?> child = entry.spawnedActors[i];

            // Set the child's supervisor field
            Shadow childShadow = getShadow(shadows, child);
            childShadow.supervisor = entry.self;
            // NB: We don't increase the parent's created count; that info is in the child snapshot.
        }

        // Local information.
        Shadow selfShadow = getShadow(shadows, entry.self);
        selfShadow.isLocal = true; // Mark it as local now that we have a snapshot from the actor.
        selfShadow.recvCount += entry.recvCount;
        selfShadow.isBusy = entry.isBusy;
        if (entry.becameRoot) {
            selfShadow.isRoot = true;
        }

        // Deactivate refs.
        for (int i = 0; i < ARRAY_MAX; i++) {
            if (entry.updatedRefs[i] == null) break;
            RefLike<?> target = entry.updatedRefs[i];
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
            RefLike<?> target = entry.updatedRefs[i];
            short info = entry.updatedInfos[i];
            short sendCount = RefobInfo.count(info);

            // Update the target's receive count
            if (sendCount > 0) {
                Shadow targetShadow = getShadow(shadows, target);
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

    public static int trace(HashMap<RefLike<?>, Shadow> shadows, boolean MARKED) {
        Shadow[] queue = new Shadow[shadows.size()];
        int allocptr = 0;
        for (Shadow shadow : shadows.values()) {
            if (isUnblocked(shadow) || isExternal(shadow)) {
                queue[allocptr] = shadow;
                allocptr++;
                shadow.mark = MARKED;
            }
        }
        for (int scanptr = 0; scanptr < allocptr; scanptr++) {
            Shadow owner = queue[scanptr];
            // Mark the outgoing references
            for (RefLike<?> targetName : owner.outgoing.keySet()) {
                Shadow target = getShadow(shadows, targetName);
                if (target.mark != MARKED && target.isLocal) {
                    queue[allocptr] = target;
                    allocptr++;
                    target.mark = MARKED;
                }
            }
            // Mark the actors that are monitoring or supervising this one
            if (owner.supervisor != null) {
                Shadow supervisor = getShadow(shadows, owner.supervisor);
                if (supervisor.mark != MARKED && supervisor.isLocal) {
                    queue[allocptr] = supervisor;
                    allocptr++;
                    supervisor.mark = MARKED;
                }
            }
        }

        // Unmarked actors are garbage. Due to supervision, an actor will only be garbage if all its descendants
        // are also garbage.
        int count = 0;
        Iterator<HashMap.Entry<RefLike<?>, Shadow>> it = shadows.entrySet().iterator();
        while (it.hasNext()) {
            HashMap.Entry<RefLike<?>, Shadow> entry = it.next();
            Shadow shadow = entry.getValue();
            if (shadow.mark != MARKED && shadow.isLocal) {
                count++;
                shadow.self.unsafeUpcast().$bang(new StopMsg());
                it.remove();
            }
        }
        return count;
    }
}
