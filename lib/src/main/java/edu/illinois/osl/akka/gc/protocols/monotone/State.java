package edu.illinois.osl.akka.gc.protocols.monotone;

import edu.illinois.osl.akka.gc.interfaces.Pretty;
import edu.illinois.osl.akka.gc.interfaces.RefLike;

public class State implements Pretty {

    /** This actor's ref to itself */
    Refob<?> self;
    /** Tracks references created by this actor */
    RefLike<?>[] createdOwners;
    RefLike<?>[] createdTargets;
    /** Tracks all the refobs that have been updated in this entry period */
    Refob<?>[] updatedRefobs;
    /** Where in the array to insert the next "created" refob */
    int createdIdx;
    /** Where in the array to insert the next "updated" refob */
    int updatedIdx;
    /** Tracks how many messages are received using each reference. */
    short recvCount;
    /** True iff the actor is a root (i.e. manually collected) */
    boolean isRoot;
    /** True if the GC has asked this actor to stop */
    boolean stopRequested;

    public State(Refob<?> self) {
        this.self = self;
        this.createdOwners = new RefLike<?>[GC.ARRAY_MAX];
        this.createdTargets = new RefLike<?>[GC.ARRAY_MAX];
        this.updatedRefobs = new Refob<?>[GC.ARRAY_MAX];
        this.createdIdx = 0;
        this.updatedIdx = 0;
        this.recvCount = (short) 0;
        this.isRoot = false;
        this.stopRequested = false;
    }

    public void markAsRoot() {
        this.isRoot = true;
    }

    public Entry onCreate(RefLike<?> owner, RefLike<?> target) {
        Entry oldEntry =
            createdIdx >= GC.ARRAY_MAX ? finalizeEntry(true) : null;
        int i = createdIdx++;
        createdOwners[i] = owner;
        createdTargets[i] = target;
        return oldEntry;
    }

    public Entry onDeactivate(Refob<?> refob) {
        refob.info_$eq(RefobInfo.deactivate(refob.info()));
        return updateRefob(refob);
    }

    public Entry onSend(Refob<?> refob) {
        refob.info_$eq(RefobInfo.incSendCount(refob.info()));
        return updateRefob(refob);
    }

    private Entry updateRefob(Refob<?> refob) {
        if (refob.hasChangedThisPeriod()) {
            // This change will automatically be reflected in the entry
            return null;
        }
        // We'll need to add to the entry; finalize first if need be
        Entry oldEntry =
            updatedIdx >= GC.ARRAY_MAX ? finalizeEntry(true) : null;
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

    public Entry getEntry() {
        Entry entry = Monotone.EntryPool().poll();
        if (entry == null) {
            entry = new Entry();
        }
        return entry;
    }

    public Entry finalizeEntry(boolean isBusy) {
        Entry entry = getEntry();
        entry.self = self.target();
        entry.isBusy = isBusy;
        entry.becameRoot = isRoot;

        for (int i = 0; i < createdIdx; i++) {
            entry.createdOwners[i] = this.createdOwners[i];
            entry.createdTargets[i] = this.createdTargets[i];
            this.createdOwners[i] = null;
            this.createdTargets[i] = null;
        }
        createdIdx = 0;

        entry.recvCount = recvCount;
        recvCount = (short) 0;

        for (int i = 0; i < updatedIdx; i++) {
            entry.updatedRefs[i] = this.updatedRefobs[i].target();
            entry.updatedInfos[i] = this.updatedRefobs[i].info();
            this.updatedRefobs[i].resetInfo();
            this.updatedRefobs[i] = null;
        }
        updatedIdx = 0;

        return entry;
    }

    @Override
    public String pretty() {
        return "[TODO: Implement Monotone.State.pretty]";
    }
}
