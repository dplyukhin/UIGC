package edu.illinois.osl.akka.gc.protocols.monotone;

public class GC {
    /** The size of each array in an entry */
    static int ARRAY_MAX = 16; // Need to use a power of 2 for the receive count

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
            boolean isDeactivated = !isActive;
            short sendCount = RefobInfo.count(info);

            Shadow targetShadow = token.targetShadow();
            int status = targetShadow.incoming.getOrDefault(token, RefobStatus.initialPendingRefob);
            boolean wasPending = RefobStatus.isPending(status);
            status = RefobStatus.addToSentCount(status, sendCount);
            if (isActive)
                status = RefobStatus.activate(status); // idempotent
            else
                status = RefobStatus.deactivate(status);
            boolean isReleased = RefobStatus.isReleased(status);

            // Adjust this actor's outgoing refobs: add activated refobs, remove released ones.
            if (isActive && wasPending) {
                // This entry must be the first one that mentions the refob
                Shadow prev = entry.shadow.outgoing.put(token, targetShadow);
                assert(prev == null);
            }
            else if (isDeactivated) {
                // This entry must be the one that deactivated the refob
                Shadow prev = entry.shadow.outgoing.remove(token);
                assert(prev == null);
            }
            if (isReleased) {
                targetShadow.incoming.remove(token);
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
                shadow.incoming.remove(token);
            }
            else {
                shadow.incoming.put(token, newStatus);
            }
        }
    }
}
