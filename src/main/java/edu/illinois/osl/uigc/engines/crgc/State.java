package edu.illinois.osl.uigc.engines.crgc;

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
    /** True if the state is full, i.e. you need to invoke {@link State#finalizeEntry}. */
    private boolean isFull;

    public State(Refob<?> self) {
        this.self = self;
        this.createdOwners = new Refob<?>[Sizes.EntryFieldSize];
        this.createdTargets = new Refob<?>[Sizes.EntryFieldSize];
        this.spawnedActors = new Refob<?>[Sizes.EntryFieldSize];
        this.updatedRefobs = new Refob<?>[Sizes.EntryFieldSize];
        this.createdIdx = 0;
        this.spawnedIdx = 0;
        this.updatedIdx = 0;
        this.recvCount = (short) 0;
        this.isRoot = false;
        this.stopRequested = false;
        this.isFull = false;
    }

    public void markAsRoot() {
        this.isRoot = true;
    }

    public void onCreate(Refob<?> owner, Refob<?> target) {
        int i = createdIdx++;
        createdOwners[i] = owner;
        createdTargets[i] = target;
        if (createdIdx >= Sizes.EntryFieldSize)
            isFull = true;
    }

    public void onSpawn(Refob<?> child) {
        spawnedActors[spawnedIdx++] = child;
        if (spawnedIdx >= Sizes.EntryFieldSize)
            isFull = true;
    }

    public void onDeactivate(Refob<?> refob) {
        boolean hasChangedThisPeriod = refob.hasChangedThisPeriod();
        refob.deactivate();

        if (!hasChangedThisPeriod) {
            updatedRefobs[updatedIdx++] = refob;
        }
        if (updatedIdx >= Sizes.EntryFieldSize)
            isFull = true;
    }

    public void onSend(Refob<?> refob) {
        boolean hasChangedThisPeriod = refob.hasChangedThisPeriod();
        refob.incSendCount();

        if (!hasChangedThisPeriod) {
            updatedRefobs[updatedIdx++] = refob;
        }
        if (updatedIdx >= Sizes.EntryFieldSize || !refob.canIncrement())
            isFull = true;
    }

    public void incReceiveCount() {
        recvCount++;
        if (recvCount == Short.MAX_VALUE)
            isFull = true;
    }

    public boolean isFull() {
        return isFull;
    }

    public Entry getEntry() {
        Entry entry = CRGC.EntryPool().poll();
        if (entry == null) {
            entry = new Entry();
        }
        return entry;
    }

    public Entry finalizeEntry(boolean isBusy) {
        isFull = false;
        Entry entry = getEntry();
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
            this.updatedRefobs[i].resetInfo();
            this.updatedRefobs[i] = null;
        }
        updatedIdx = 0;

        return entry;
    }

}
