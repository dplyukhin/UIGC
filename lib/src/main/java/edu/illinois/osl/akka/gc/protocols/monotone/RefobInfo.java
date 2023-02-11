package edu.illinois.osl.akka.gc.protocols.monotone;

/**
 * RefobInfo consists of a message send count and a status bit indicating
 * whether the actor is deactivated. This is packed into a short
 * whose most significant bit is on iff the refob has been deactivated.
 */
class RefobInfo {
    public static short activeRefob = 0;

    public static short incSendCount(short info) {
        return info++;
    }

    public static boolean isActive(short info) {
        return info >= 0;
    }

    public static short deactivate(short info) {
        return (short) (info | (1 << 15));
    }
}