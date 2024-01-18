package edu.illinois.osl.uigc.engines.crgc;

import akka.actor.ActorRef;
import akka.actor.Address;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * The undo log indicates how many messages should be marked "un-sent" and how many references
 * should be marked "deactivated" after some ActorSystems have left the cluster. (This terminology
 * is a bit misleading because the values can be negative. But it's good to have a mental model.)
 */
public class UndoLog {
    Address nodeAddress;
    HashSet<Address> finalizedBy;
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

    public UndoLog(Address nodeAddress) {
        this.nodeAddress = nodeAddress;
        this.finalizedBy = new HashSet<>();
        this.admitted = new HashMap<>();
    }

    public void mergeDeltaGraph(DeltaGraph delta) {
        // This will act as a hashmap, mapping compressed IDs to actorRefs.
        ActorRef[] decoder = delta.decoder();

        for (short i = 0; i < delta.size; i++) {
            DeltaShadow deltaShadow = delta.shadows[i];
            if (deltaShadow.interned)
                // We only care about messages sent and references created by this node
                // *for actors on other nodes*.
                continue;

            ActorRef thisActor = decoder[i];
            Field field = admitted.get(thisActor);
            if (field == null) {
                field = new Field();
                admitted.put(thisActor, field);
            }

            // Undo any messages this node claims to have sent to the recipient actor
            field.messageCount -= deltaShadow.recvCount;

            // Undo any of the references this node claims to have created for the recipient
            for (Map.Entry<Short, Integer> entry : deltaShadow.outgoing.entrySet()) {
                ActorRef targetActor = decoder[entry.getKey()];
                int count = entry.getValue();
                updateOutgoing(field.createdRefs, targetActor, -count);
            }
        }
    }

    public void mergeIngressEntry(IngressEntry entry) {
        for (Map.Entry<ActorRef, IngressEntry.Field> pair : entry.admitted.entrySet()) {
            ActorRef actor = pair.getKey();
            IngressEntry.Field entryField = pair.getValue();

            Field field = this.admitted.get(actor);
            if (field == null) {
                field = new Field();
                admitted.put(actor, field);
            }

            // Add back the number of messages that actually got admitted to the node.
            field.messageCount += entryField.messageCount;

            for (Map.Entry<ActorRef, Integer> refCount : entryField.createdRefs.entrySet()) {
                ActorRef targetActor = refCount.getKey();
                int count = refCount.getValue();
                // Add back the references that actually got delivered to the node.
                updateOutgoing(field.createdRefs, targetActor, count);
            }
        }
        if (entry.isFinal) {
            finalizedBy.add(entry.ingressAddress);
        }
    }

    public void updateOutgoing(Map<ActorRef, Integer> outgoing, ActorRef target, int delta) {
        int count = outgoing.getOrDefault(target, 0);
        if (count + delta == 0) {
            // Instead of writing zero, we delete the count.
            outgoing.remove(target);
        }
        else {
            outgoing.put(target, count + delta);
        }
    }
}
