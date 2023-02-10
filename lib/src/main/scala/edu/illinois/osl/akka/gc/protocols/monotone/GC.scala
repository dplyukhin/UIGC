package edu.illinois.osl.akka.gc.protocols.monotone

import scala.collection.mutable

object GC {
    type Ref
    class Entry (
        val created: mutable.ArrayBuffer[Ref],
        val sentCount: mutable.HashMap[Token, Int],
        val recvCount: mutable.HashMap[Token, Int]
    )
    type History
    class Shadow {
        /** A shadow is marked if it is potentially unblocked in the current history */
        val isMarked: Boolean = false
        /** 
         * A list of unreleased refobs pointing to this actor.
         */
        val incoming: mutable.HashMap[Token,RefobStatus] = mutable.HashMap()
        /**
         * A list of unreleased refobs pointing from this actor.
         */
        val outgoing: mutable.HashMap[Ref, Shadow] = mutable.HashMap()
    }

    def onEntry(entry: Entry, history: History): Unit = {
        for (fact <- entry.created) {
            // In each of these cases, we need to look up the target actor's 
            // shadow and update it.
            // NB: The actor might not have a shadow yet!
            // ...and in future, the shadow may represent an actor in a remote system
            // If the token doesn't already exist, create the initial pending refob
            // If it does, do nothing


        }
        // Here we also need to update those shadows
        for ((token, n) <- entry.sentCount) {
            ???
            // If it's nonexistent, use the initial active refob
            // If it's pending, activate it first
            // If it's active, add the count

            // If it's active...
                // If the token doesn't already exist, create the initial active refob
                // If it does, activate it. It cannot already be active or pending.
            // If it's deactivated...
                // Hmm... We should only process this fact after we have adjusted the 
                // send count.
        }
        for ((token, m) <- entry.recvCount) {
            val shadow: Shadow = ??? // Look up this actor's shadow
            val incoming = shadow.incoming
            val status = incoming.getOrElse(token, initialPendingRefob)
            val newStatus = addToRecvCount(status, m)
            if (refobIsReleased(status)) {
                incoming -= token
            }
            else {
                incoming(token) = newStatus
            }
        }
        // Adjust this actor's outgoing refobs: add activated refobs, remove
        // released ones.
        ???
    }

    // TODO Move this to Java to avoid the wrapper
    /** 
     * A refob's status is either:
     * 1. Pending: Created, but not activated, with N received messages.
     * 2. Active: Activated, with N pending messages.
     * 3. Finishing: Deactivated, with N > 0 pending messages.
     * 4. Released: Deactivated, with 0 pending messages.
     * 
     * The number of pending messages is the difference between the owner's send
     * count and the target's receive count. If these values are not defined,
     * then they default to zero.
     * 
     * Note that if a refob is active, then it may have a negative number of 
     * pending messages. If a refob is finishing, then it can only have a
     * positive number of pending messages.
     * 
     * We encode these four states with a single integer. The 31 highest-order
     * bits encode a 31-bit integer. The lowest order bit is a marker.
     * 1. If the marker is on and the integer is negative, then the refob is
     *    pending. The value of the integer indicates how many messages have
     *    been received.
     * 2. If the marker is off, then the integer encodes the number of pending
     *    messages.
     * 3. If the marker is on and the integer is N > 0, then the refob is 
     *    finishing with N pending messages.
     * 4. If the marker is on and the integer is 0, then the refob is released.
     * 
     * Some conveniences of this representation:
     * - Released refobs are represented as the integer 1.
     * - Active, blocked refobs are represented as the integer 0.
     * 
     * If an actor has any unreleased refobs whose status is nonzero, then the
     * actor is potentially unblocked in the history. Either the history is not 
     * recent enough, not full enough, or a refob is unblocked.
     */
    type RefobStatus = Integer

    val initialPendingRefob: RefobStatus = -1

    val initialActiveRefob: RefobStatus = 0

    def addToSentCount(n: RefobStatus, m: Integer): RefobStatus =
        // assumes n is an active refob
        return n + (m << 1)

    def addToRecvCount(n: RefobStatus, m: Integer): RefobStatus =
        // n may be pending, active, or finishing
        return n - (m << 1)

    def activateRefob(n: RefobStatus): RefobStatus =
        // assumes n is a pending refob
        return n | 1

    def deactivateRefob(n: RefobStatus): RefobStatus =
        // assumes n is an active refob
        return n | 1

    def refobIsReleased(n: RefobStatus): Boolean =
        return n == 1

    def refobStatus(n: RefobStatus): String = {
        if ((n & 1) == 0) // Status bit is off
            return s"ACTIVE with ${n/2} pending messages"
        else if (n < 0) // Status bit is on and the int is negative
            return s"PENDING with ${(n+1)/2} received messages"
        else if (n > 1) // Status bit is on and the int is positive
            return s"FINISHING with ${(n-1)/2} pending messages"
        else            // n == 1, status bit is on and the int is zero
            return "RELEASED"
    }
}