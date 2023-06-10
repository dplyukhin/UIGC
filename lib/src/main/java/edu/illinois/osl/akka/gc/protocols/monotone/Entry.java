package edu.illinois.osl.akka.gc.protocols.monotone;

import edu.illinois.osl.akka.gc.interfaces.RefLike;

import java.util.Arrays;

public class Entry {
    public RefLike<?> self;
    public RefLike<?>[] createdOwners;
    public RefLike<?>[] createdTargets;
    public RefLike<?>[] updatedRefs;
    public short[] updatedInfos;
    public short recvCount;
    public boolean isBusy;
    public boolean becameRoot;

    public Entry() {
        self           = null;
        createdOwners  = new RefLike<?>[GC.ARRAY_MAX];
        createdTargets = new RefLike<?>[GC.ARRAY_MAX];
        updatedRefs    = new RefLike<?>[GC.ARRAY_MAX];
        updatedInfos   = new short[GC.ARRAY_MAX];
        isBusy         = false;
        becameRoot     = false;
    }

    public void clean() {
        self = null;
        Arrays.fill(createdOwners, null);
        Arrays.fill(createdTargets, null);
        Arrays.fill(updatedRefs, null);
        Arrays.fill(updatedInfos, (short) 0);
        isBusy = false;
        becameRoot = false;
    }
}