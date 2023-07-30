package edu.illinois.osl.akka.gc.protocols.monotone;

import java.util.Arrays;
import java.util.HashMap;

public class Entry {
    public Refob<?> self;
    public Refob<?>[] createdOwners;
    public Refob<?>[] createdTargets;
    public Refob<?>[] updatedRefs;
    public Refob<?>[] spawnedActors;
    HashMap<SomeRef, Boolean> monitoredRefobs;
    public short[] updatedInfos;
    public short recvCount;
    public boolean isBusy;
    public boolean isRoot;
    public boolean isHalted;

    public Entry() {
        self            = null;
        createdOwners   = new Refob<?>[Sizes.EntryFieldSize];
        createdTargets  = new Refob<?>[Sizes.EntryFieldSize];
        updatedRefs     = new Refob<?>[Sizes.EntryFieldSize];
        spawnedActors  = new Refob<?>[Sizes.EntryFieldSize];
        monitoredRefobs = new HashMap<>(Sizes.EntryFieldSize * 5 / 4, 0.75F);
            // We set the initial capacity so the default load factor of 0.75 will never be exceeded.
        updatedInfos    = new short[Sizes.EntryFieldSize];
        recvCount       = 0;
        isBusy          = false;
        isRoot          = false;
        isHalted        = false;
    }

    public void clean() {
        self = null;
        Arrays.fill(createdOwners, null);
        Arrays.fill(createdTargets, null);
        Arrays.fill(updatedRefs, null);
        Arrays.fill(updatedInfos, (short) 0);
        Arrays.fill(spawnedActors, null);
        monitoredRefobs.clear();
        recvCount = 0;
        isBusy = false;
        isRoot = false;
        isHalted = false;
    }
}