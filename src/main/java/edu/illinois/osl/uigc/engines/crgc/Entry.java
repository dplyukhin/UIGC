package edu.illinois.osl.uigc.engines.crgc;

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
    public boolean isRoot;

    public Entry(Context context) {
        self           = null;
        createdOwners  = new Refob<?>[context.EntryFieldSize];
        createdTargets = new Refob<?>[context.EntryFieldSize];
        spawnedActors  = new Refob<?>[context.EntryFieldSize];
        updatedRefs    = new Refob<?>[context.EntryFieldSize];
        updatedInfos   = new short[context.EntryFieldSize];
        isBusy         = false;
        isRoot         = false;
    }

    public void clean() {
        self = null;
        Arrays.fill(createdOwners, null);
        Arrays.fill(createdTargets, null);
        Arrays.fill(spawnedActors, null);
        Arrays.fill(updatedRefs, null);
        Arrays.fill(updatedInfos, (short) 0);
        isBusy = false;
        isRoot = false;
    }
}
