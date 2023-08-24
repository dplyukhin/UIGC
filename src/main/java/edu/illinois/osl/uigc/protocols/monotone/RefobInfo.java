package edu.illinois.osl.uigc.protocols.monotone;

/**
 * RefobInfo consists of a message send count and a status bit indicating
 * whether the refob is deactivated. This is packed into a short
 * whose least significant bit is on iff the refob has been deactivated.
 */
public class RefobInfo {
    public static short activeRefob = 0;

    public static boolean canIncrement(short info) {
        return info <= Short.MAX_VALUE - 2;
    }

    public static short incSendCount(short info) {
        return (short) (info + 2);
    }

    public static short resetCount(short info) {
        return 0;
    }

    public static short count(short info) {
        return (short) (info >> 1);
    }

    public static boolean isActive(short info) {
        return (info & 1) == 0;
    }

    public static short deactivate(short info) {
        // Idempotent
        return (short) (info | 1);
    }
}
