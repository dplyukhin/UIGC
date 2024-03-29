package edu.illinois.osl.uigc.engines.crgc.jfr;

import jdk.jfr.*;

@Label("CRGC Merging Ingress Entries")
@Category("UIGC")
@Description("Local GC merging ingress entries.")
@StackTrace(false)
public class MergingIngressEntries extends Event {

    @Label("Sender")
    public String sender;

}
