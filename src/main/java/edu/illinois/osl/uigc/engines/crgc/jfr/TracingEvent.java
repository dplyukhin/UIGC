package edu.illinois.osl.uigc.engines.crgc.jfr;

import jdk.jfr.*;

@Label("CRGC Tracing Event")
@Category("UIGC")
@Description("Tracing the shadow graph and asking garbage actors to stop.")
@StackTrace(false)
public class TracingEvent extends Event {
}
