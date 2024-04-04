package edu.illinois.osl.uigc.engines.crgc.jfr;

import jdk.jfr.*;

@Label("CRGC Delta Graph Serialized")
@Category("UIGC")
@Description("A delta graph is serialized.")
@StackTrace(false)
public class DeltaGraphSerialization extends Event {

    @Label("Size of Shadow Array")
    @DataAmount
    public long shadowSize;

    @Label("Size of Compression Table")
    @DataAmount
    public long compressionTableSize;
}
