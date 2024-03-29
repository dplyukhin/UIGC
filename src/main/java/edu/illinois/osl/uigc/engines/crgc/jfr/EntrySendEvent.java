package edu.illinois.osl.uigc.engines.crgc.jfr;

import jdk.jfr.*;

@Label("CRGC Entry Sent")
@Category("UIGC")
@Description("An actor flushing an update about its local GC state to the local garbage collector.")
@StackTrace(false)
@Enabled(false)
public class EntrySendEvent extends Event {

    @Label("Was memory allocated?")
    public boolean allocatedMemory;

}
