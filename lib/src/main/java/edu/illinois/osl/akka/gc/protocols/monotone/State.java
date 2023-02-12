package edu.illinois.osl.akka.gc.protocols.monotone;

import edu.illinois.osl.akka.gc.interfaces.Pretty;

import java.util.Arrays;

class State implements Pretty {

    static int ARRAY_MAX = 8; // Use a power of 2 for the receive count

    /** A sequence number used for generating unique tokens */
    int count;
    /** Where in the array to insert the next "created" refob */
    int createdIdx;
    /** Where in the array to insert the next "updated" refob */
    int updatedIdx;
    /** This actor's ref to itself */
    Object selfRef;
    /** Tracks references created by this actor */
    Refob<?>[] created;
    /** Tracks all the refs that have been updated in this entry period */
    Refob<?>[] updated;
    /** Tracks how many messages are received using each reference. */
    ReceiveCount recvCount;

    public State() {
        this.count = 0;
        this.createdIdx = 0;
        this.created = new Refob<?>[ARRAY_MAX];
        this.updated = new Refob<?>[ARRAY_MAX];
        this.recvCount = new ReceiveCount(ARRAY_MAX);
    }

    public void onCreate(Refob<?> ref) {
        created[createdIdx++] = ref;
        if (createdIdx >= ARRAY_MAX) {
            finalizeEntry();
        }
    }

    public void onReceive(Refob<?> ref) {
        ref.initialize(this);
        ref.hasChangedThisPeriod_$eq(true);
        updated[updatedIdx++] = ref;
        if (updatedIdx >= ARRAY_MAX) {
            finalizeEntry();
        }
    }

    public void onDeactivate(Refob<?> ref) {
        ref.info_$eq(RefobInfo.deactivate(ref.info()));
        if (!ref.hasChangedThisPeriod()) {
            ref.hasChangedThisPeriod_$eq(true);
            updated[updatedIdx++] = ref;
            if (updatedIdx >= ARRAY_MAX) {
                finalizeEntry();
            }
        }
    }

    public void onSend(Refob<?> ref) {
        ref.info_$eq(RefobInfo.incSendCount(ref.info()));
        if (!ref.hasChangedThisPeriod()) {
            ref.hasChangedThisPeriod_$eq(true);
            updated[updatedIdx++] = ref;
            if (updatedIdx >= ARRAY_MAX) {
                finalizeEntry();
            }
        }
    }

    public void incReceiveCount(Token token) {
        recvCount.incCount(token);
        if (recvCount.size >= ARRAY_MAX) {
            finalizeEntry();
        }
    }

    public Entry getEntry() {
        Entry entry = Monotone.EntryPool().poll();
        if (entry == null) {
            entry = new Entry(ARRAY_MAX);
        }
        return entry;
    }

    public void putEntry(Entry entry) {
        Arrays.fill(entry.created, null);
        Arrays.fill(entry.recvTokens, null);
        Arrays.fill(entry.recvCounts, (short) 0);
        Arrays.fill(entry.sendTokens, null);
        Arrays.fill(entry.sendInfos, (short) 0);
        Monotone.EntryPool().add(entry);
    }

    public Entry finalizeEntry() {
        Entry entry = getEntry();

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

        putEntry(entry);
        return null;
    }

    @Override
    public String pretty() {
        return "[TODO: Implement Monotone.State.pretty]";
    }
}
