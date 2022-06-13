
ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

lazy val commonScalacOptions = Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-Ywarn-unused:imports",
  "-encoding", "UTF-8"
)

lazy val commonJavacOptions = Seq(
  "-Xlint:unchecked",
  "-Xlint:deprecation"
)

lazy val commonSettings = Seq(
  Compile / scalacOptions ++= commonScalacOptions,
  Compile / javacOptions ++= commonJavacOptions,
  run / javaOptions ++= Seq("-Xms128m", "-Xmx1024m"),
  run / fork := false,
  Global / cancelable := false,
)

val akkaVersion       = "2.6.19"
val akkaHttpVersion   = "10.2.9"
val logbackVersion    = "1.2.9"
val leveldbVersion    = "0.7"
val leveldbjniVersion = "1.8"

lazy val forClustering = (project in file("."))
  .settings(commonSettings)
  .settings(
    name := "for_clustering",
    libraryDependencies ++= Seq(
      "com.typesafe.akka"         %% "akka-actor-typed"            % akkaVersion,
      "com.typesafe.akka"         %% "akka-cluster-sharding-typed" % akkaVersion,
      "com.typesafe.akka"         %% "akka-persistence-typed"      % akkaVersion,
      "com.typesafe.akka"         %% "akka-serialization-jackson"  % akkaVersion,
      "com.typesafe.akka"         %% "akka-distributed-data"       % akkaVersion,
      "com.typesafe.akka"         %% "akka-slf4j"                  % akkaVersion,
      "com.typesafe.akka"         %% "akka-http"                   % akkaHttpVersion,
      "com.typesafe.akka"         %% "akka-http-spray-json"        % akkaHttpVersion,
      "ch.qos.logback"            %  "logback-classic"             % logbackVersion,
      "org.iq80.leveldb"          % "leveldb"                      % leveldbVersion,
      "org.fusesource.leveldbjni" % "leveldbjni-all"               % leveldbjniVersion,
    )
  )


