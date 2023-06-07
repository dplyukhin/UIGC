package edu.illinois.osl.akka.gc.protocols.monotone;

public class ReceiveCount {
    public int size;
    public Token[] tokens;
    public short[] counts;

    public ReceiveCount() {
        size = 0;
        tokens = new Token[GC.ARRAY_MAX];
        counts = new short[GC.ARRAY_MAX];
    }

    public void incCount(Token token) {
        // Precondition: size < capacity and token != null
        int idx = token.hashCode() & (GC.ARRAY_MAX - 1);
        while (tokens[idx] != token && tokens[idx] != null) {
            idx = (idx + 1) % GC.ARRAY_MAX;
        }
        if (tokens[idx] == null) {
            // Setting this field for the first time
            tokens[idx] = token;
            counts[idx] = 1;
            size++;
        }
        else { // tokens[idx] == token
            counts[idx]++;
        }
    }

    public void copyOut(Entry entry) {
        int nextWrite = 0;
        for (int i = 0; i < GC.ARRAY_MAX; i++) {
            if (counts[i] == 0) continue;
            entry.recvTokens[nextWrite] = tokens[i];
            tokens[i] = null;
            entry.recvCounts[nextWrite] = counts[i];
            counts[i] = 0;
            nextWrite++;
        }
    }
}
