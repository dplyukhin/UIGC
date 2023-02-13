package edu.illinois.osl.akka.gc.protocols.monotone;

/**
 * A refob's status is either:
 * 1. Pending: Created, but not activated, with N <= 0 pending messages.
 * 2. Active: Activated, with N pending messages.
 * 3. Finishing: Deactivated, with N > 0 pending messages.
 * 4. Released: Deactivated, with 0 pending messages.
 *
 * The number of pending messages is the difference between the owner's send
 * count and the target's receive count. Note that this value may be negative.
 * The send count and the receive count each default to zero if they are not
 * defined.
 *
 * Note that if a refob is active or pending, then it may have a negative number
 * of pending messages. If a refob is finishing, then it can only have a
 * positive number of pending messages.
 *
 * We encode these four states with a single integer. The 30 highest-order
 * bits encode a 30-bit integer. The two lowest bits encode the status:
 * 1. Status 00 indicates active.
 * 2. Status 01 indicates pending.
 * 3. Status 10 indicates deactivated, i.e. finishing or released.
 *    a. A refob is finishing if it is deactivated with a nonzero count.
 *    b. A refob is released if it is deactivated with a zero coung.
 *
 * Active, blocked refobs are represented as the integer 0, making it easy to
 * check if an actor is blocked.
 */
public class RefobStatus {

    final private static int STATUS_BITS = 3;
    final private static int ACTIVE_MASK = 0;
    final private static int PENDING_MASK = 1;
    final private static int DEACTIVATED_MASK = 2;

    public static int initialPendingRefob = 1;

    public static int addToSentCount(int status, int m) {
        return status + (m << 2);
    }

    public static int addToRecvCount(int status, int m) {
        return status - (m << 2);
    }

    public static int activate(int status) {
        return (status & ~STATUS_BITS) | ACTIVE_MASK;
    }

    public static int deactivate(int status) {
        return (status & ~STATUS_BITS) | DEACTIVATED_MASK;
    }

    public static int count(int status) {
        return status >> 2;
    }

    public static boolean isPending(int status) {
        return (status & STATUS_BITS) == PENDING_MASK;
    }

    public static boolean hasBeenActivated(int status) {
        return !isPending(status);
    }

    public static boolean isActive(int status) {
        return (status & STATUS_BITS) == ACTIVE_MASK;
    }

    public static boolean hasBeenDeactivated(int status) {
        return (status & STATUS_BITS) == DEACTIVATED_MASK;
    }

    public static boolean isFinishing(int status) {
        return hasBeenDeactivated(status) && count(status) != 0;
    }

    public static boolean isReleased(int status) {
        return status == DEACTIVATED_MASK;
    }
}
