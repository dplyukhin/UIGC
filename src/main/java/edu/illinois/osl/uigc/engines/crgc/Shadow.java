package edu.illinois.osl.uigc.engines.crgc;

import akka.actor.Address;
import akka.actor.ActorRef;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Shadow {
    ActorRef self;
    Address location;
    /**
     * A collection of actors that this actor may be acquainted with. The value of <tt>outgoing(b)</tt> is the
     * number of references this actor appears to have for <tt>b</tt>. The value may be negative, meaning this
     * actor has deactivated more references than have been created for it.
     */
    HashMap<Shadow, Integer> outgoing;
    /**
     * This actor's parent, if it is known; {@code null} otherwise.
     */
    Shadow supervisor;
    /**
     * The difference between the number of messages this actor has received and the number of messages that
     * have been sent to this actor. May be positive or negative.
     */
    int recvCount;
    //int markDepth;
    /** A marker bit, used by {@link ShadowGraph} during tracing. */
    boolean mark;
    /** Indicates whether the actor was a root in its latest entry. */
    boolean isRoot;
    /** Indicates whether the GC has received an entry from this actor yet. */
    boolean interned;
    /** Indicates whether this actor is on the same node as this GC. */
    boolean isLocal;
    /** Whether this actor was busy in its latest entry. */
    boolean isBusy;
    /** Whether this actor has permanently stopped. */
    boolean isHalted;

    public Shadow() {
        this.outgoing = new HashMap<>();
        this.self = null;
        this.supervisor = null;
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
                ", \nself=" + self +
                ", \nsupervisor=" + (supervisor == null ? "null" : supervisor.self) +
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
