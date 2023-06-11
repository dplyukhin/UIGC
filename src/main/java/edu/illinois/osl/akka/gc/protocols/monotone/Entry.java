package edu.illinois.osl.akka.gc.protocols.monotone;

import java.util.Arrays;

public class Entry {
    public Refob<?> self;
    public Refob<?>[] createdOwners;
    public Refob<?>[] createdTargets;
    public Refob<?>[] spawnedActors;
    public Refob<?>[] updatedRefs;
    public short[] updatedInfos;
    public short recvCount;
    public boolean isBusy;
    public boolean becameRoot;

    public Entry() {
        self           = null;
        createdOwners  = new Refob<?>[GC.ARRAY_MAX];
        createdTargets = new Refob<?>[GC.ARRAY_MAX];
        spawnedActors  = new Refob<?>[GC.ARRAY_MAX];
        updatedRefs    = new Refob<?>[GC.ARRAY_MAX];
        updatedInfos   = new short[GC.ARRAY_MAX];
        isBusy         = false;
        becameRoot     = false;
    }

    public void clean() {
        self = null;
        Arrays.fill(createdOwners, null);
        Arrays.fill(createdTargets, null);
        Arrays.fill(spawnedActors, null);
        Arrays.fill(updatedRefs, null);
        Arrays.fill(updatedInfos, (short) 0);
        isBusy = false;
        becameRoot = false;
    }
}