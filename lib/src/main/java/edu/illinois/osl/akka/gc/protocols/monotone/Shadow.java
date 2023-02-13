package edu.illinois.osl.akka.gc.protocols.monotone;

import java.util.HashMap;

public class Shadow {
    /** A list of unreleased refobs pointing to this actor. */
    HashMap<Token, Integer> incoming;
    /** A list of active refobs pointing from this actor. */
    HashMap<Token, Shadow> outgoing;
    /** A shadow is marked if it is potentially unblocked in the current history */
    boolean isMarked;
    boolean isRoot;

    public Shadow() {
        incoming = new HashMap<>();
        outgoing = new HashMap<>();
        isMarked = false;
        isRoot = false;
    }
}
