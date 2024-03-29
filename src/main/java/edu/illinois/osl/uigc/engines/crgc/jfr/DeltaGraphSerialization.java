package edu.illinois.osl.uigc.engines.crgc.jfr;

import jdk.jfr.*;

@Label("CRGC Delta Graph Serialized")
@Category("UIGC")
@Description("A delta graph is serialized.")
@StackTrace(false)
public class DeltaGraphSerialization extends Event {

    @Label("Size")
    @DataAmount
    public long size;

}
