package edu.illinois.osl.akka.gc.protocols.monotone;

import java.util.Arrays;

public class Entry {
    public Refob<?> self;
    public Refob<?>[] createdOwners;
    public Refob<?>[] createdTargets;
    public Refob<?>[] updatedRefs;
    public short[] updatedInfos;
    public short recvCount;
    public boolean isBusy;
    public boolean isRoot;

    public Entry() {
        self           = null;
        createdOwners  = new Refob<?>[Sizes.EntryFieldSize];
        createdTargets = new Refob<?>[Sizes.EntryFieldSize];
        updatedRefs    = new Refob<?>[Sizes.EntryFieldSize];
        updatedInfos   = new short[Sizes.EntryFieldSize];
        isBusy         = false;
        isRoot = false;
    }

    public void clean() {
        self = null;
        Arrays.fill(createdOwners, null);
        Arrays.fill(createdTargets, null);
        Arrays.fill(updatedRefs, null);
        Arrays.fill(updatedInfos, (short) 0);
        isBusy = false;
        isRoot = false;
    }
}