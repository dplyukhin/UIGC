package edu.illinois.osl.akka.gc.protocols.monotone;

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
}