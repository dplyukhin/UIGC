package edu.illinois.osl.akka.gc.protocols.monotone;

import java.util.Arrays;

public class Entry {
    public Refob<?>[] created;
    public Token[] recvTokens;
    public short[] recvCounts;
    public Token[] sendTokens;
    public short[] sendInfos;

    public Entry(int ARRAY_MAX) {
        created    = new Refob<?>[ARRAY_MAX];
        recvTokens = new Token[ARRAY_MAX];
        recvCounts = new short[ARRAY_MAX];
        sendTokens = new Token[ARRAY_MAX];
        sendInfos  = new short[ARRAY_MAX];
    }

    public void clean() {
        Arrays.fill(created, null);
        Arrays.fill(recvTokens, null);
        Arrays.fill(recvCounts, (short) 0);
        Arrays.fill(sendTokens, null);
        Arrays.fill(sendInfos, (short) 0);
    }
}