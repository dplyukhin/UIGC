package edu.illinois.osl.akka.gc.protocols.monotone

object GC {
    type ActorRef
    type Entry
    type History
    class Shadow {
        /** A shadow is marked if it is potentially unblocked in the current history */
        val isMarked: Boolean = false
        /** 
         * A list of unreleased refobs pointing to this actor.
         */
        val incoming: Map[Token,RefobStatus] = Map()
        /**
         * A list of this actor's potential inverse acquaintances.
         */
        val potentialInverseAcquaintances: Seq[ActorRef] = Seq()
    }

    /** 
     * A refob's status is either:
     * 1. Pending: Created, but not activated.
     * 2. Active: Activated, with N pending messages.
     * 3. Inactive: Deactivated, with N > 0 pending messages.
     * 4. Released: Deactivated, with 0 pending messages.
     * 
     * The number of pending messages is the difference between the owner's send
     * count and the target's receive count. If these values are not defined,
     * then they default to zero.
     * 
     * Note that if a refob is active, then it may have a negative number of 
     * pending messages. If a refob is inactive, then it can only have a
     * positive number of pending messages.
     * 
     * We encode these four states with a single integer. The 31 highest-order
     * bits encode a 31-bit integer. The lowest order bit is a marker.
     * 1. If the marker is on and the integer is negative, then the refob is
     *    pending. Aside from being negative, the value of the integer does not
     *    matter.
     * 2. If the marker is off, then the integer encodes the number of pending
     *    messages.
     * 3. If the marker is on and the integer is N > 0, then the refob is 
     *    inactive with N pending messages.
     * 4. If the marker is on and the integer is 0, then the refob is released.
     * 
     * Some conveniences of this representation:
     * - Adding and subtracting from the count is fast; no branching logic.
     * - Released refobs are represented as the integer 1.
     * - Active, blocked refobs are represented as the integer 0.
     * 
     * If an actor has any unreleased refobs whose status is nonzero, then the
     * actor is potentially unblocked in the history. Either the history is not 
     * recent enough, not full enough, or a refob is unblocked.
     */
    type RefobStatus = Integer

    val pendingRefob: RefobStatus = -1

    val initialActiveRefob: RefobStatus = 0

    def addToRefob(n: RefobStatus, m: Integer): RefobStatus =
        return n + 2*m

    def subFromRefob(n: RefobStatus, m: Integer): RefobStatus =
        return n - 2*m

    def deactivateRefob(n: RefobStatus): RefobStatus =
        return n + 1

    def refobStatus(n: RefobStatus): String = {
        if (n % 2 == 0) // Status bit is off
            return s"ACTIVE with ${n/2} pending messages"
        else if (n < 0) // Status bit is on and the int is negative
            return "PENDING"
        else if (n > 1) // Status bit is on and the int is positive
            return s"INACTIVE with ${(n-1)/2} pending messages"
        else            // n == 1, status bit is on and the int is zero
            return "RELEASED"
    }
}