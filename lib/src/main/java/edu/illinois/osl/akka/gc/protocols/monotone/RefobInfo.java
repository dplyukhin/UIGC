package edu.illinois.osl.akka.gc.protocols.monotone;

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