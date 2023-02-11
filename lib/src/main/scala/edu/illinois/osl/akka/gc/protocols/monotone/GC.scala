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
            //val shadow: Shadow = ??? // Look up this actor's shadow
            //val incoming = shadow.incoming
            //val status = incoming.getOrElse(token, initialPendingRefob)
            //val newStatus = addToRecvCount(status, m)
            //if (refobIsReleased(status)) {
            //    incoming -= token
            //}
            //else {
            //    incoming(token) = newStatus
            //}
        }
        // Adjust this actor's outgoing refobs: add activated refobs, remove
        // released ones.
        ???
    }

    type RefobStatus = Integer

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