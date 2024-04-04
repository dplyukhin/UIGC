package edu.illinois.osl.uigc.engines.crgc;

import edu.illinois.osl.uigc.engines.crgc.jfr.EntryFlushEvent;

public class State implements edu.illinois.osl.uigc.interfaces.State {

    /** This actor's ref to itself */
    Refob<?> self;
    /** Tracks references created by this actor */
    Refob<?>[] createdOwners;
    Refob<?>[] createdTargets;
    /** Tracks actors spawned by this actor */
    Refob<?>[] spawnedActors;
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
    /** True if the GC has asked this actor to stop */
    boolean stopRequested;
    Context context;

    public State(Refob<?> self, Context context) {
        this.self = self;
        this.context = context;
        this.createdOwners = new Refob<?>[context.EntryFieldSize];
        this.createdTargets = new Refob<?>[context.EntryFieldSize];
        this.spawnedActors = new Refob<?>[context.EntryFieldSize];
        this.updatedRefobs = new Refob<?>[context.EntryFieldSize];
        this.createdIdx = 0;
        this.spawnedIdx = 0;
        this.updatedIdx = 0;
        this.recvCount = (short) 0;
        this.isRoot = false;
        this.stopRequested = false;
    }

    public void markAsRoot() {
        this.isRoot = true;
    }

    public boolean canRecordNewRefob() {
        return createdIdx < context.EntryFieldSize;
    }

    public void recordNewRefob(Refob<?> owner, Refob<?> target) {
        assert(canRecordNewRefob());
        int i = createdIdx++;
        createdOwners[i] = owner;
        createdTargets[i] = target;
    }

    public boolean canRecordNewActor() {
        return spawnedIdx < context.EntryFieldSize;
    }

    public void recordNewActor(Refob<?> child) {
        assert(canRecordNewActor());
        spawnedActors[spawnedIdx++] = child;
    }

    public boolean canRecordUpdatedRefob(Refob<?> refob) {
        return refob.hasBeenRecorded() || updatedIdx < context.EntryFieldSize;
    }

    public void recordUpdatedRefob(Refob<?> refob) {
        assert(canRecordUpdatedRefob(refob));
        if (refob.hasBeenRecorded())
            return;
        refob.setHasBeenRecorded();
        updatedRefobs[updatedIdx++] = refob;
    }

    public boolean canRecordMessageReceived() {
        return recvCount < Short.MAX_VALUE;
    }

    public void recordMessageReceived() {
        assert(canRecordMessageReceived());
        recvCount++;
    }

    public void flushToEntry(boolean isBusy, Entry entry) {
        EntryFlushEvent metrics = new EntryFlushEvent();
        metrics.recvCount = recvCount;

        entry.self = self;
        entry.isBusy = isBusy;
        entry.isRoot = isRoot;

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
            this.updatedRefobs[i].reset();
            this.updatedRefobs[i] = null;
        }
        updatedIdx = 0;

        metrics.commit();
    }

}
