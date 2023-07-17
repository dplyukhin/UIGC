This library requires a [forked version of Akka](https://github.com/dplyukhin/akka).

Distributed GC requires the following configuration settings:
``` 
akka.remote.artery.advanced.ingress-stage = "edu.illinois.osl.akka.gc.streams.Ingress"
akka.remote.artery.advanced.egress-stage = "edu.illinois.osl.akka.gc.streams.Egress"
```