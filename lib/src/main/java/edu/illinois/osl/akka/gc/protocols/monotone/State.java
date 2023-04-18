package edu.illinois.osl.akka.gc.protocols.monotone;

import edu.illinois.osl.akka.gc.interfaces.Pretty;

public class State implements Pretty {

    /** A sequence number used for generating unique tokens */
    int count;
    /** Where in the array to insert the next "created" refob */
    int createdIdx;
    /** Where in the array to insert the next "updated" refob */
    int updatedIdx;
    /** True if the GC has asked this actor to stop */
    boolean stopRequested;
    /** This actor's shadow */
    Shadow shadow;
    /** This actor's ref to itself */
    Refob<Object> selfRef;
    /** Tracks references created by this actor */
    Refob<?>[] created;
    /** Tracks all the refs that have been updated in this entry period */
    Refob<?>[] updated;
    /** Tracks how many messages are received using each reference. */
    ReceiveCount recvCount;

    public State(Shadow shadow) {
        this.count = 0;
        this.shadow = shadow;
        this.createdIdx = 0;
        this.created = new Refob<?>[GC.ARRAY_MAX];
        this.updated = new Refob<?>[GC.ARRAY_MAX];
        this.recvCount = new ReceiveCount();
    }

    public Entry onCreate(Refob<?> ref) {
        created[createdIdx++] = ref;
        if (createdIdx >= GC.ARRAY_MAX) {
            return finalizeEntry(true);
        }
        return null;
    }

    public Entry onActivate(Refob<?> ref) {
        ref.hasChangedThisPeriod_$eq(true);
        updated[updatedIdx++] = ref;
        if (updatedIdx >= GC.ARRAY_MAX) {
            return finalizeEntry(true);
        }
        return null;
    }

    public Entry onDeactivate(Refob<?> ref) {
        ref.info_$eq(RefobInfo.deactivate(ref.info()));
        if (!ref.hasChangedThisPeriod()) {
            ref.hasChangedThisPeriod_$eq(true);
            updated[updatedIdx++] = ref;
            if (updatedIdx >= GC.ARRAY_MAX) {
                return finalizeEntry(true);
            }
        }
        return null;
    }

    public Entry onSend(Refob<?> ref) {
        ref.info_$eq(RefobInfo.incSendCount(ref.info()));
        if (!ref.hasChangedThisPeriod()) {
            ref.hasChangedThisPeriod_$eq(true);
            updated[updatedIdx++] = ref;
            if (updatedIdx >= GC.ARRAY_MAX) {
                return finalizeEntry(true);
            }
        }
        return null;
    }

    public Entry incReceiveCount(Token token) {
        recvCount.incCount(token);
        if (recvCount.size >= GC.ARRAY_MAX) {
            return finalizeEntry(true);
        }
        return null;
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
        entry.self = selfRef.target();
        entry.shadow = shadow;
        entry.isBusy = isBusy;

        if (createdIdx > 0) {
            for (int i = 0; i < createdIdx; i++) {
                entry.created[i] = this.created[i];
                this.created[i] = null;
            }
            createdIdx = 0;
        }

        if (recvCount.size > 0) {
            recvCount.copyOut(entry);
        }

        if (updatedIdx > 0) {
            for (int i = 0; i < updatedIdx; i++) {
                entry.sendTokens[i] = this.updated[i].token().get();
                entry.sendInfos[i] = this.updated[i].info();
                this.updated[i].resetInfo();
                updated[i] = null;
            }
            updatedIdx = 0;
        }

        return entry;
    }

    @Override
    public String pretty() {
        return "[TODO: Implement Monotone.State.pretty]";
    }
}