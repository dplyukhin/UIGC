package edu.illinois.osl.uigc.engines.crgc.jfr;

import jdk.jfr.*;

@Label("CRGC Ingress Entry Serialized")
@Category("UIGC")
@Description("An ingress entry is serialized.")
@StackTrace(false)
public class IngressEntrySerialization extends Event {

    @Label("Size")
    @DataAmount
    public long size;

}
