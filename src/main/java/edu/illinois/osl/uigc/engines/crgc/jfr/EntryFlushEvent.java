package edu.illinois.osl.uigc.engines.crgc.jfr;

import jdk.jfr.*;

@Label("CRGC Entry Flushed")
@Category("UIGC")
@Description("An actor recording statistics for the garbage collector.")
@StackTrace(false)
@Enabled(false)
public class EntryFlushEvent extends Event {

    @Label("Number of Messages Received")
    public int recvCount;

}
