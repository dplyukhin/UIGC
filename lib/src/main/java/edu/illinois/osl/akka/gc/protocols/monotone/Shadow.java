package edu.illinois.osl.akka.gc.protocols.monotone;

import edu.illinois.osl.akka.gc.interfaces.RefLike;

import java.util.HashMap;

public class Shadow {
    /** A list of active refobs pointing from this actor. */
    HashMap<RefLike<?>, Integer> outgoing;
    int recvCount;
    boolean mark;
    boolean isRoot;
    /** Indicates whether the GC has received a copy of this shadow yet. */
    boolean isLocal;
    /** Whether this actor was busy in its latest entry. */
    boolean isBusy;
    /** A reference to the actor. Only initialized if isLocal is true. */
    RefLike<GCMessage<Object>> ref;

    public Shadow(boolean isLocal) {
        this.outgoing = new HashMap<>();
        this.recvCount = 0;
        this.mark = false;
        this.isRoot = false;
        this.isLocal = isLocal;
        this.isBusy = false;
    }
}
