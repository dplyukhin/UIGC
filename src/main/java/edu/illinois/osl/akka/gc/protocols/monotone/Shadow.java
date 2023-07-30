package edu.illinois.osl.akka.gc.protocols.monotone;

import akka.actor.Address;
import akka.actor.ActorRef;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

public class Shadow {
    /** A list of active refobs pointing from this actor. */
    ActorRef self;
    Shadow supervisor;
    HashMap<Shadow, Integer> outgoing;
    HashSet<Shadow> watchers;
    Address location;
    int recvCount;
    //int markDepth;
    boolean mark;
    boolean isRoot;
    /** Indicates whether the GC has received a snapshot from this actor yet. */
    boolean interned;
    /** Indicates whether the actor is on the same node as this GC. */
    boolean isLocal;
    /** Whether this actor was busy in its latest entry. */
    boolean isBusy;
    /** Whether this actor has permanently stopped. */
    boolean isHalted;

    public Shadow() {
        this.self = null;
        this.supervisor = null;
        this.outgoing = new HashMap<>();
        this.watchers = new HashSet<>();
        this.recvCount = 0;
        //this.markDepth = 0;
        this.mark = false;
        this.isRoot = false;
        this.interned = false;
        this.isLocal = false;
        this.isBusy = false;
        this.isHalted = false;
    }

    @Override
    public String toString() {
        return "Shadow{" +
                "\noutgoing=" + outgoing.keySet().stream().map(x -> x.self) +
                ", \nwatchers=" + watchers.stream().map(x -> x.self) +
                ", \nsupervisor=" + (supervisor == null ? "null" : supervisor.self) +
                ", \nself=" + self +
                ", \nrecvCount=" + recvCount +
                //", \nmarkDepth=" + markDepth +
                ", \nmark=" + mark +
                ", \nisRoot=" + isRoot +
                ", \ninterned=" + interned +
                ", \nisLocal=" + isLocal +
                ", \nisBusy=" + isBusy +
                "}\n";
    }

    /** Compare two shadows from distinct graphs for debugging purposes. */
    public void assertEquals(Shadow that) {
        // TODO Compare the watcher sets!
        assert (this.self == that.self)
                : this + " was not " + that;
        assert ((this.supervisor != null && that.supervisor != null) || (this.supervisor == null && that.supervisor == null))
                : this + " was not " + that;
        assert (this.supervisor == null || (this.supervisor.self == that.supervisor.self))
                : this + " was not " + that;
        assert (this.recvCount == that.recvCount)
                : this + " was not " + that;
        assert (this.isRoot == that.isRoot)
                : this + " was not " + that;
        assert (this.interned == that.interned)
                : this + " was not " + that;
        assert (this.isBusy == that.isBusy)
                : this + " was not " + that;
        for (Map.Entry<Shadow, Integer> thisEntry : this.outgoing.entrySet()) {
            boolean anyMatch = false;
            assert(that != null);
            assert(that.outgoing != null);
            for (Map.Entry<Shadow, Integer> thatEntry : that.outgoing.entrySet()) {
                if (thisEntry.getKey().self == thatEntry.getKey().self) {
                    anyMatch = true;
                    assert (Objects.equals(thisEntry.getValue(), thatEntry.getValue()))
                            : thisEntry + " was not " + thatEntry;
                }
            }
            assert anyMatch
                : this + " was not " + that;
        }
    }
}
