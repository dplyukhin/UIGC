package edu.illinois.osl.uigc.engines.crgc.jfr;

import jdk.jfr.*;

@Label("CRGC Merging Delta Graphs")
@Category("UIGC")
@Description("Local GC merging remote delta graphs into the local graph.")
@StackTrace(false)
public class MergingDeltaGraphs extends Event {

    @Label("Sender")
    public String sender;

}
