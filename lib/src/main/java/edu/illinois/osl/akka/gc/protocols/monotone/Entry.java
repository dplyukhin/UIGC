package edu.illinois.osl.akka.gc.protocols.monotone;

record Entry(
    Refob<?>[] created,
    Token[] recvTokens,
    short[] recvCounts,
    Token[] sendTokens,
    short[] sendInfo
) {
}