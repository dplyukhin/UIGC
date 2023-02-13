package edu.illinois.osl.akka.gc.protocols.monotone;

import edu.illinois.osl.akka.gc.interfaces.RefLike;

import java.util.Arrays;

public class Entry {
    public RefLike<GCMessage<Object>> self;
    public Shadow shadow;
    public Refob<?>[] created;
    public Token[] recvTokens;
    public short[] recvCounts;
    public Token[] sendTokens;
    public short[] sendInfos;

    public Entry() {
        self       = null;
        shadow     = null;
        created    = new Refob<?>[GC.ARRAY_MAX];
        recvTokens = new Token[GC.ARRAY_MAX];
        recvCounts = new short[GC.ARRAY_MAX];
        sendTokens = new Token[GC.ARRAY_MAX];
        sendInfos  = new short[GC.ARRAY_MAX];
    }

    public void clean() {
        self   = null;
        shadow = null;
        Arrays.fill(created, null);
        Arrays.fill(recvTokens, null);
        Arrays.fill(recvCounts, (short) 0);
        Arrays.fill(sendTokens, null);
        Arrays.fill(sendInfos, (short) 0);
    }
}