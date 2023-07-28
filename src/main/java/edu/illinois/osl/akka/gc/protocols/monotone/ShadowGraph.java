package edu.illinois.osl.akka.gc.protocols.monotone;

import akka.actor.Address;
import akka.actor.typed.ActorRef;

import java.util.*;

public class ShadowGraph {
    /** The size of each array in an entry */
    boolean MARKED = true;
    int totalActorsSeen = 0;
    ArrayList<Shadow> from;
    HashMap<ActorRef<?>, Shadow> shadowMap;

    public ShadowGraph() {
        from = new ArrayList<>();
        shadowMap = new HashMap<>();
    }

    public Shadow getShadow(Refob<?> refob) {
        // Check if it's in the cache.
        //if (refob.targetShadow() != null)
        //    return refob.targetShadow();

        // Try to get it from the collection of all my shadows. Save it in the cache.
        Shadow shadow = getShadow(refob.target());
        //refob.targetShadow_$eq(shadow);

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
        totalActorsSeen++;
        // Haven't heard of this actor yet. Create a shadow for it.
        Shadow shadow = new Shadow();
        shadow.location = ref.path().address();
        shadow.self = ref;
        shadow.mark = !MARKED;
            // The value of MARKED flips on every GC scan. Make sure this shadow is unmarked.
        shadow.interned = false;
            // We haven't seen this shadow before, so we can't have received a snapshot from it.
        shadow.isLocal = false;
            // By default we assume that the shadow is from a different node. If the shadow
            // graph gets an entry from the actor, then it turns out the actor is local.

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

            shadow.interned = shadow.interned || deltaShadow.interned;
                // Set `interned` if we have already received a delta shadow in which
                // the actor was interned, or if the actor was interned in this delta.
            shadow.recvCount += deltaShadow.recvCount;
            if (deltaShadow.interned) {
                // Careful here! The isBusy and isRoot fields are only accurate if
                // the delta shadow is interned, i.e. we received an entry from this
                // actor in the given period. Otherwise, they are set at the default
                // value of `false`.
                shadow.isBusy = deltaShadow.isBusy;
                shadow.isRoot = deltaShadow.isRoot;
            }
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
        HashSet<ActorRef<?>> thisNotThat = new HashSet<>();
        for (ActorRef<?> ref : this.shadowMap.keySet()) {
            if (!that.shadowMap.containsKey(ref)) {
                thisNotThat.add(ref);
            }
        }
        HashSet<ActorRef<?>> thatNotThis = new HashSet<>();
        for (ActorRef<?> ref : that.shadowMap.keySet()) {
            if (!this.shadowMap.containsKey(ref)) {
                thatNotThis.add(ref);
            }
        }
        assert (this.shadowMap.keySet().equals(that.shadowMap.keySet()))
                : "Shadow maps have different actors:\n"
                + "Actors in this, not that: " + thisNotThat + "\n"
                + "Actors in that, not this " + thatNotThis;

        for (Map.Entry<ActorRef<?>, Shadow> entry : this.shadowMap.entrySet()) {
            Shadow thisShadow = entry.getValue();
            Shadow thatShadow = that.shadowMap.get(entry.getKey());
            thisShadow.assertEquals(thatShadow);
        }
    }

    private static boolean isUnblocked(Shadow shadow) {
        return shadow.isRoot || shadow.isBusy || shadow.recvCount != 0;
    }

    public int trace(boolean shouldKill) {
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
                //shadow.markDepth = 1;
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
                    //target.markDepth = owner.markDepth + 1;
                }
                //if (entry.getValue() > 0 && target.markDepth > owner.markDepth + 1) {
                //    target.markDepth = owner.markDepth + 1;
                //}
            }
            // Mark the actors that are monitoring or supervising this one
            Shadow supervisor = owner.supervisor;
            if (supervisor != null) {
                if (supervisor.mark != MARKED) {
                    to.add(supervisor);
                    supervisor.mark = MARKED;
                }
                //if (supervisor.markDepth > owner.markDepth + 1) {
                //    supervisor.markDepth = owner.markDepth + 1;
                //}
            }
        }

        // Unmarked actors are garbage. Due to supervision, an actor will only be garbage if all its descendants
        // are also garbage.
        int count = 0;
        for (Shadow shadow : from) {
            if (shadow.mark != MARKED) {
                count++;
                shadowMap.remove(shadow.self);
                if (shadow.isLocal && shadow.supervisor.mark == MARKED && shouldKill) {
                    shadow.self.unsafeUpcast().tell(StopMsg$.MODULE$);
                }
            }
        }
        from = to;
        MARKED = !MARKED;
        return count;
    }

    public void startWave() {
        int count = 0;
        for (Shadow shadow : from) {
            if (shadow.isRoot && shadow.isLocal) {
                count++;
                shadow.self.unsafeUpcast().tell(WaveMsg$.MODULE$);
            }
        }
    }

    /** Debugging method to look at how many actors are reachable by actors at `location`. */
    public int investigateRemotelyHeldActors(Address location) {
        // Mark everything reachable by `location`.
        ArrayList<Shadow> to = new ArrayList<>();
        for (Shadow shadow : from) {
            if (shadow.location.equals(location)) {
                to.add(shadow);
                shadow.mark = MARKED;
            }
        }

        for (int scanptr = 0; scanptr < to.size(); scanptr++) {
            Shadow owner = to.get(scanptr);
            for (Map.Entry<Shadow, Integer> entry : owner.outgoing.entrySet()) {
                Shadow target = entry.getKey();
                if (entry.getValue() > 0 && target.mark != MARKED) {
                    to.add(target);
                    target.mark = MARKED;
                }
            }
        }

        // Now unmark all those actors, resetting the state so we can do GC again.
        for (Shadow shadow : to) {
            shadow.mark = !MARKED;
        }
        return to.size();
    }

    public void addressesInGraph() {
        HashMap<Address, Integer> addresses = new HashMap<>();
        for (Shadow shadow : from) {
            int count = addresses.getOrDefault(shadow.location, 0);
            addresses.put(shadow.location, count+1);
        }
        for (Map.Entry<Address, Integer> entry : addresses.entrySet()) {
            System.out.println(entry.getValue() + " uncollected at " + entry.getKey());
        }
    }

    /** Debugging method to dump information about the live set. */
    public void investigateLiveSet() {
        int nonInternedActors = 0;
        int rootActors = 0;
        int busyActors = 0;
        int unblockedActors = 0;
        int nonLocalActors = 0;
        HashMap<Integer, Integer> markDepths = new HashMap<>();
        for (Shadow shadow : from) {
            if (!shadow.interned) nonInternedActors++;
            if (shadow.isRoot) {
                rootActors++;
                System.out.println(shadow.outgoing.size() + " acquaintances of root actor " + shadow.self);
            }
            if (shadow.isBusy) busyActors++;
            if (shadow.recvCount != 0) unblockedActors++;
            if (!shadow.isLocal) nonLocalActors++;

            //int x = markDepths.getOrDefault(shadow.markDepth, 0);
            //markDepths.put(shadow.markDepth, x + 1);

            if (shadow.isLocal) {
                int c = 0;
                for (Shadow out : shadow.outgoing.keySet()) {
                    if (!out.isLocal) {
                        c++;
                        System.out.println("Local " + shadow.self + " appears acquainted with remote " + out.self + " (" + shadow.outgoing.get(out) + ")");
                    }
                }
                if (c > 0)
                    System.out.println("Local " + shadow.self + " has " + c + " nonlocal apparent acquaintances.");
            }
            else {
                int c = 0;
                for (Shadow out : shadow.outgoing.keySet()) {
                    if (out.isLocal) c++;
                }
                if (c > 0)
                    System.out.println("Remote " + shadow.self + " has " + c + " apparent acquaintances that are local to this node.");
            }
        }
        System.out.println(
                nonInternedActors + " actors not yet interned;\n" +
                rootActors + " root actors;\n" +
                busyActors + " busy actors;\n" +
                nonLocalActors + " nonlocal actors;\n" +
                unblockedActors + " actors have nonzero receive counts.\n"
        );
        for (int depth : markDepths.keySet()) {
            System.out.println(markDepths.get(depth) + " actors at mark depth " + depth + "\n");
        }
    }
}
