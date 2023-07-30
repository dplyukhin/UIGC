package edu.illinois.osl.akka.gc.protocols.monotone;

import edu.illinois.osl.akka.gc.interfaces.Pretty;

import java.util.HashMap;
import java.util.HashSet;

public class State implements Pretty {

    /** This actor's ref to itself */
    Refob<?> self;
    /** Tracks references created by this actor */
    Refob<?>[] createdOwners;
    Refob<?>[] createdTargets;
    /** Tracks actors spawned by this actor */
    Refob<?>[] spawnedActors;
    /** Tracks refobs that have been monitored/unmonitored.
     * If an actor became monitored in the entry period, the value is true.
     * If the actor became unmonitored, the value is false. */
    HashMap<SomeRef, Boolean> monitoredRefobs;
    /** Tracks all the refobs that have been updated in this entry period */
    Refob<?>[] updatedRefobs;
    /** Where in the array to insert the next "created" ref */
    int createdIdx;
    /** Where in the array to insert the next "spawned" ref */
    int spawnedIdx;
    /** Where in the array to insert the next "updated" refob */
    int updatedIdx;
    /** Tracks how many messages are received using each reference. */
    short recvCount;
    /** True iff the actor is a root (i.e. manually collected) */
    boolean isRoot;
    /** True iff the actor has thrown an exception */
    boolean isHalted;
    /** True if the GC has asked this actor to stop */
    boolean stopRequested;

    public State(Refob<?> self) {
        this.self = self;
        this.createdOwners = new Refob<?>[Sizes.EntryFieldSize];
        this.createdTargets = new Refob<?>[Sizes.EntryFieldSize];
        this.spawnedActors = new Refob<?>[Sizes.EntryFieldSize];
        this.monitoredRefobs = new HashMap<>(Sizes.EntryFieldSize * 5 / 4, 0.75F);
            // We set the initial capacity so the default load factor of 0.75 will never be exceeded.
        this.updatedRefobs = new Refob<?>[Sizes.EntryFieldSize];
        this.createdIdx = 0;
        this.spawnedIdx = 0;
        this.updatedIdx = 0;
        this.recvCount = (short) 0;
        this.isRoot = false;
        this.isHalted = false;
        this.stopRequested = false;
    }

    public void markAsRoot() {
        this.isRoot = true;
    }

    public Entry onCreate(Refob<?> owner, Refob<?> target) {
        Entry oldEntry =
            createdIdx >= Sizes.EntryFieldSize ? finalizeEntry(true) : null;
        int i = createdIdx++;
        createdOwners[i] = owner;
        createdTargets[i] = target;
        return oldEntry;
    }

    public Entry onSpawn(Refob<?> child) {
        Entry oldEntry =
                spawnedIdx >= Sizes.EntryFieldSize ? finalizeEntry(true) : null;
        int i = spawnedIdx++;
        spawnedActors[i] = child;
        return oldEntry;
    }

    public Entry onMonitor(SomeRef target) {
        Entry oldEntry =
                monitoredRefobs.size() >= Sizes.EntryFieldSize ? finalizeEntry(true) : null;
        Boolean isPositive = monitoredRefobs.get(target);
        if (isPositive == null) {
            // target does not occur in the set
            monitoredRefobs.put(target, true);
        }
        else if (!isPositive) {
            // target occurs negatively in the set, i.e. we just unmonitored it and have already
            // begun monitoring again. The two actions cancel out.
            monitoredRefobs.remove(target);
        }
        return oldEntry;
    }

    public Entry onUnmonitor(SomeRef target) {
        Entry oldEntry =
                monitoredRefobs.size() >= Sizes.EntryFieldSize ? finalizeEntry(true) : null;
        Boolean isPositive = monitoredRefobs.get(target);
        if (isPositive == null) {
            // target does not occur in the set; we began monitoring at an earlier period.
            monitoredRefobs.put(target, false);
        }
        else if (isPositive) {
            // target occurs positively in the set, i.e. we just monitored it and have already
            // stopped monitoring. The two actions cancel out.
            monitoredRefobs.remove(target);
        }
        return oldEntry;
    }

    public Entry onDeactivate(Refob<?> refob) {
        refob.info_$eq(RefobInfo.deactivate(refob.info()));
        return updateRefob(refob);
    }

    public Entry onSend(Refob<?> refob) {
        if (RefobInfo.canIncrement(refob.info())) {
            refob.info_$eq(RefobInfo.incSendCount(refob.info()));
            return updateRefob(refob);
        }
        else {
            Entry oldEntry = finalizeEntry(true);
                // Now the counter has been reset
            refob.info_$eq(RefobInfo.incSendCount(refob.info()));
            updateRefob(refob);
                // We know this will not overflow because we have a fresh entry.
            return oldEntry;
        }
    }

    private Entry updateRefob(Refob<?> refob) {
        if (refob.hasChangedThisPeriod()) {
            // This change will automatically be reflected in the entry
            return null;
        }
        // We'll need to add to the entry; finalize first if need be
        Entry oldEntry =
            updatedIdx >= Sizes.EntryFieldSize ? finalizeEntry(true) : null;
        refob.hasChangedThisPeriod_$eq(true);
        updatedRefobs[updatedIdx++] = refob;
        return oldEntry;
    }

    public Entry incReceiveCount() {
        Entry oldEntry =
            recvCount == Short.MAX_VALUE ? finalizeEntry(true) : null;
        recvCount++;
        return oldEntry;
    }

    public Entry onThrow() {
        isHalted = true;
        return finalizeEntry(false);
    }

    public Entry getEntry() {
        Entry entry = Monotone.EntryPool().poll();
        if (entry == null) {
            entry = new Entry();
        }
        return entry;
    }

    public Entry finalizeEntry(boolean isBusy) {
        Entry entry = getEntry();
        entry.self = self;
        entry.isBusy = isBusy;
        entry.isRoot = isRoot;
        entry.isHalted = isHalted;

        for (int i = 0; i < createdIdx; i++) {
            entry.createdOwners[i] = this.createdOwners[i];
            entry.createdTargets[i] = this.createdTargets[i];
            this.createdOwners[i] = null;
            this.createdTargets[i] = null;
        }
        createdIdx = 0;

        for (int i = 0; i < spawnedIdx; i++) {
            entry.spawnedActors[i] = this.spawnedActors[i];
            this.spawnedActors[i] = null;
        }
        spawnedIdx = 0;

        entry.recvCount = recvCount;
        recvCount = (short) 0;

        for (int i = 0; i < updatedIdx; i++) {
            entry.updatedRefs[i] = this.updatedRefobs[i];
            entry.updatedInfos[i] = this.updatedRefobs[i].info();
            this.updatedRefobs[i].resetInfo();
            this.updatedRefobs[i] = null;
        }
        updatedIdx = 0;

        // Trade ya! The entry's monitoredRefobs is empty, so we can just swap pointers.
        HashMap<SomeRef, Boolean> tmp = entry.monitoredRefobs;
        entry.monitoredRefobs = this.monitoredRefobs;
        this.monitoredRefobs = tmp;

        return entry;
    }

    @Override
    public String pretty() {
        return "[TODO: Implement Monotone.State.pretty]";
    }
}
