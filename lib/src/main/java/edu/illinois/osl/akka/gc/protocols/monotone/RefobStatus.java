package edu.illinois.osl.akka.gc.protocols.monotone;

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
class RefobStatus {
  
    public static int initialPendingRefob = -1;

    public static int initialActiveRefob = 0;

    public static int addToSentCount(int status, int m) {
        // assumes n is an active refob
        return status + (m << 1);
    }

    public static int addToRecvCount(int status, int m) {
        // n may be pending, active, or finishing
        return status - (m << 1);
    }

    public static int activateRefob(int status) {
        // assumes n is a pending refob
        return status | 1;
    }

    public static int deactivateRefob(int status) {
        // assumes n is an active refob
        return status | 1;
    }

    public static boolean refobIsReleased(int status) {
        return status == 1;
    }
}
