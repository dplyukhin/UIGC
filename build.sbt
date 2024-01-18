val org = "edu.illinois.osl"
val libVersion = "0.1.0-SNAPSHOT"
val akkaVersion = "2.8.0-M3+18-6fadd9a8+20230727-1556-SNAPSHOT"

ThisBuild / scalaVersion     := "2.13.12"
ThisBuild / version          := libVersion
ThisBuild / organization     := org

lazy val lib = (project in file("."))
  .settings(
    name := "uigc",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.1.1" % "test",
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-remote" % akkaVersion,
      "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.scalacheck" %% "scalacheck" % "1.14.1" % "test",
    ),
    scalacOptions in Compile ++= Seq(
      "-optimise", 
      "-Xdisable-assertions"
    )
  )