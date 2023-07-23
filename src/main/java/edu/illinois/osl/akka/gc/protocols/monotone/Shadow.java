package edu.illinois.osl.akka.gc.protocols.monotone;

import edu.illinois.osl.akka.gc.interfaces.RefLike;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Shadow {
    /** A list of active refobs pointing from this actor. */
    HashMap<Shadow, Integer> outgoing;
    RefLike<?> self;
    Shadow supervisor;
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

    /** Compare two shadows from distinct graphs for debugging purposes. */
    public void assertEquals(Shadow that) {
        assert (this.self == that.self)
                : this.self + " was not " + that.self;
        assert ((this.supervisor != null && that.supervisor != null) || (this.supervisor == null && that.supervisor == null))
                : this.supervisor + " was not " + that.supervisor;
        assert (this.supervisor == null || (this.supervisor.self == that.supervisor.self))
                : this.supervisor.self + " was not " + that.supervisor.self;
        assert (this.recvCount == that.recvCount)
                : this.recvCount + " was not " + that.recvCount;
        assert (this.isRoot == that.isRoot)
                : this.isRoot + " was not " + that.isRoot;
        assert (this.isLocal == that.isLocal)
                : this.isLocal + " was not " + that.isLocal;
        assert (this.isBusy == that.isBusy)
                : this.isBusy + " was not " + that.isBusy;
        for (Map.Entry<Shadow, Integer> thisEntry : this.outgoing.entrySet()) {
            boolean anyMatch = false;
            for (Map.Entry<Shadow, Integer> thatEntry : that.outgoing.entrySet()) {
                if (thisEntry.getKey().self == thatEntry.getKey().self) {
                    anyMatch = true;
                    assert (Objects.equals(thisEntry.getValue(), thatEntry.getValue()))
                            : thisEntry + " was not " + thatEntry;
                }
            }
            assert anyMatch :
                    outgoing.entrySet() + " contains entry for " + thisEntry.getKey().self
                    + ", not present in " + that.outgoing.entrySet();
        }
    }
}
