package edu.illinois.osl.akka.gc.protocols.monotone;

import java.util.HashMap;

public class Shadow {
    /** A list of active refobs pointing from this actor. */
    HashMap<Refob<?>, Integer> outgoing;
    Refob<?> self;
    Refob<?> supervisor;
    int recvCount;
    boolean mark;
    boolean isRoot;
    /** Indicates whether the GC has received a snapshot from this actor yet. */
    boolean isLocal;
    /** Whether this actor was busy in its latest entry. */
    boolean isBusy;

    public Shadow() {
        this.outgoing = new HashMap<>();
        this.self = null;
        this.supervisor = null;
        this.recvCount = 0;
        this.mark = false;
        this.isRoot = false;
        this.isLocal = false;
        this.isBusy = false;
    }
}
