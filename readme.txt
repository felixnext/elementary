NOTE:
The Play-FrontEnd (frontend) is not hosted by the main git file, due to two reasons:
1. Play requieres activator for live debugging, which is not compatible with using plain sbt
2. Packages can be build in sbt using the assembly package (sbt assembly), play requires activator dist

PIPELINE:
There can be new seed nodes added at startup of the jvm:
-Dakka.cluster.seed-nodes.0=akka.tcp://ClusterSystem@host1:2552
