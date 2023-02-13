package edu.illinois.osl.akka.gc.protocols.monotone;

import edu.illinois.osl.akka.gc.interfaces.RefLike;

import java.util.HashMap;

public class Shadow {
    /** A list of unreleased refobs pointing to this actor. */
    HashMap<Token, Integer> incoming;
    /** A list of active refobs pointing from this actor. */
    HashMap<Token, Shadow> outgoing;
    boolean mark;
    boolean isRoot;
    /** Indicates whether the GC has received a copy of this shadow yet. */
    boolean isLocal;
    /** A reference to the actor. Only initialized if isLocal is true. */
    RefLike<GCMessage<Object>> ref;

    public Shadow() {
        incoming = new HashMap<>();
        outgoing = new HashMap<>();
        mark = false;
        isRoot = false;
        isLocal = false;
    }
}
