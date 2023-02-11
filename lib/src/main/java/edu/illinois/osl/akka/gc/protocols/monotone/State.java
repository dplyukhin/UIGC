package edu.illinois.osl.akka.gc.protocols.monotone;

import edu.illinois.osl.akka.gc.interfaces.Pretty;

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

    public Entry finalizeEntry() {
        Refob<?>[] _created = null;
        if (createdIdx > 0) {
            _created = new Refob<?>[createdIdx];
            for (int i = 0; i < createdIdx; i++) {
                _created[i] = this.created[i];
                this.created[i] = null;
            }
            createdIdx = 0;
        }

        Token[] recvTokens = null;
        short[] recvCounts = null;
        if (recvCount.size > 0) {
            recvTokens = new Token[recvCount.size];
            recvCounts = new short[recvCount.size];
            recvCount.copyOut(recvTokens, recvCounts);
        }

        Token[] sendTokens = null;
        short[] sendInfos = null;
        if (updatedIdx > 0) {
            sendTokens = new Token[updatedIdx];
            sendInfos = new short[updatedIdx];
            for (int i = 0; i < updatedIdx; i++) {
                sendTokens[i] = this.updated[i].token().get();
                sendInfos[i] = this.updated[i].info();
                this.updated[i].resetInfo();
                updated[i] = null;
            }
            updatedIdx = 0;
        }

        return new Entry(_created, recvTokens, recvCounts, sendTokens, sendInfos);
    }

    @Override
    public String pretty() {
        return "[TODO: Implement Monotone.State.pretty]";
    }
}
