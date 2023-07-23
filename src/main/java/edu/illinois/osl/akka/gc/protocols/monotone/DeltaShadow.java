package edu.illinois.osl.akka.gc.protocols.monotone;

import edu.illinois.osl.akka.gc.interfaces.CborSerializable;

import java.util.HashMap;

public class DeltaShadow implements CborSerializable {
    HashMap<Short, Integer> outgoing;
    short supervisor;
    int recvCount;
    boolean isRoot;
    boolean isBusy;
    boolean isLocal;
        // This field will be set to `true` if any of the entries in this batch were
        // produced by this actor.

    public DeltaShadow() {
        this.outgoing = new HashMap<>();
        this.supervisor = -1; // Set to an invalid value if it didn't change
        this.recvCount = 0;
        this.isRoot = false;
        this.isBusy = false;
        this.isLocal = false;
    }
}
