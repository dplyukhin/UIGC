package edu.illinois.osl.uigc.engines.mac.jfr;

import jdk.jfr.*;

@Label("MAC Actor Blocked")
@Category("UIGC")
@Description("An actor finished processing messages in its mail queue.")
@StackTrace(false)
public class ActorBlockedEvent extends Event {
    @Label("Application Messages Received")
    public int appMsgCount;

    @Label("Control Messages Received")
    public int ctrlMsgCount;
}
