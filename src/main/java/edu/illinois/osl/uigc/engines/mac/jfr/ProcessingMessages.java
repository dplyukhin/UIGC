package edu.illinois.osl.uigc.engines.mac.jfr;

import jdk.jfr.*;

@Label("MAC Cycle Detector Wakeup")
@Category("UIGC")
@Description("The cycle detector wakes up and processes messages in its mail queue.")
@StackTrace(false)
public class ProcessingMessages extends Event {

    @Label("Messages Processed")
    public int numMessages;

}
