package edu.illinois.osl.akka.gc.protocols.monotone;

public class GC {
    /** The size of each array in an entry */
    static int ARRAY_MAX = 16; // Use a power of 2 for the receive count

    public static void processEntry(Entry entry) {
        // Created refs
        for (int i = 0; i < ARRAY_MAX; i++) {
            if (entry.created[i] == null) break;
            if (entry.created[i].token().isDefined()) {
                Token token = entry.created[i].token().get();
                // Update the target actor's shadow.
                Shadow shadow = token.targetShadow();
                // If the token doesn't already exist, create the initial pending refob.
                // If it does, do nothing.
                shadow.incoming.putIfAbsent(token, RefobStatus.initialPendingRefob);
            }
            else {
                entry.shadow.isRoot = true;
                // TODO I'm assuming actors only create root refs to themselves
            }
        }
        // Sent info
        for (int i = 0; i < ARRAY_MAX; i++) {
            if (entry.sendTokens[i] == null) break;
            Token token = entry.sendTokens[i];

            short info = entry.sendInfos[i];
            boolean isActive = RefobInfo.isActive(info);
            short sendCount = RefobInfo.count(info);

            Shadow targetShadow = token.targetShadow();
            int status = targetShadow.incoming.getOrDefault(token, RefobStatus.initialPendingRefob);
            boolean wasPending = RefobStatus.isPending(status);
            if (isActive && wasPending)
                status = RefobStatus.activate(status);
            else
                status = RefobStatus.deactivate(status);
            status = RefobStatus.addToSentCount(status, sendCount);

            // Adjust this actor's outgoing refobs: add activated refobs, remove released ones.
            if (isActive && wasPending) {
                entry.shadow.outgoing.put(token, targetShadow);
            }
            if (RefobStatus.isReleased(status)) {
                // delete it from the set. // TODO Maybe we only need outgoing *active* refs?
                targetShadow.incoming.remove(token);
                entry.shadow.outgoing.remove(token);
            }
        }
        // Receive counts
        for (int i = 0; i < ARRAY_MAX; i++) {
            if (entry.recvTokens[i] == null) break;
            Token token = entry.recvTokens[i];
            short count = entry.recvCounts[i];
            Shadow shadow = entry.shadow;
            int status = shadow.incoming.getOrDefault(token, RefobStatus.initialPendingRefob);
            int newStatus = RefobStatus.addToRecvCount(status, count);
            if (RefobStatus.isReleased(newStatus)) {
                // TODO Update the other actor too?
                shadow.incoming.remove(token);
            }
            else {
                shadow.incoming.put(token, newStatus);
            }
        }
    }
}
