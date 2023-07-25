package edu.illinois.osl.akka.gc.protocols.monotone;

import akka.actor.ActorRef;
import akka.actor.Address;

import java.io.Serializable;
import java.util.HashMap;

/**
 * The undo log indicates how many messages should be marked "un-sent" and how many references
 * should be marked "deactivated" after some ActorSystems have left the cluster. (This terminology
 * is a bit misleading because the values can be negative. But it's good to have a mental model.)
 */
public class UndoLog {
    /** The address of the node whose effects we are undoing. */
    Address nodeAddress;
    /** The highest sequence number delta graph that has been merged into the log so far. */
    int deltaSeqnum;
    /** For each node adjacent to [[nodeAddress]], this field indicates the highest sequence number
     * of an ingress actor at that node. */
    HashMap<Address, Integer> ingressSeqnum;
    HashMap<ActorRef, Field> admitted;

    public static class Field implements Serializable {
        int messageCount;
        HashMap<ActorRef, Integer> createdRefs;
        // We could probably be smarter about the data structure based on
        // empirical study.

        public Field() {
            this.messageCount = 0;
            this.createdRefs = new HashMap<>();
        }
    }

    public void mergeDeltaGraph(DeltaGraph delta) {

    }
}
