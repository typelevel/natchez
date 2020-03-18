lazy val scala212Version = "2.12.10"
lazy val scala213Version = "2.13.1"

// Global Settings
lazy val commonSettings = Seq(

  // Publishing
  organization := "org.tpolecat",
  licenses    ++= Seq(("MIT", url("http://opensource.org/licenses/MIT"))),
  homepage     := Some(url("https://github.com/tpolecat/natchez")),
  developers   := List(
    Developer("tpolecat", "Rob Norris", "rob_norris@mac.com", url("http://www.tpolecat.org"))
  ),

  // Headers
  headerMappings := headerMappings.value + (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
  headerLicense  := Some(HeaderLicense.Custom(
    """|Copyright (c) 2019 by Rob Norris
       |This software is licensed under the MIT License (MIT).
       |For more information see LICENSE or https://opensource.org/licenses/MIT
       |""".stripMargin
    )
  ),

  // Compilation
  scalaVersion       := scala212Version,
  crossScalaVersions := Seq(scala212Version, scala213Version),
  Compile / console / scalacOptions --= Seq("-Xfatal-warnings", "-Ywarn-unused:imports"),
  Compile / doc     / scalacOptions --= Seq("-Xfatal-warnings"),
  Compile / doc     / scalacOptions ++= Seq(
    "-groups",
    "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath,
    "-doc-source-url", "https://github.com/tpolecat/natchez/blob/v" + version.value + "€{FILE_PATH}.scala"
  ),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full),

  // Blah
  resolvers +=
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

)

lazy val natchez = project
  .in(file("."))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    crossScalaVersions := Nil,
    publish / skip     := true
  )
  .dependsOn(core, jaeger, honeycomb, opencensus, lightstep, lightstepGrpc, lightstepHttp, log, examples)
  .aggregate(core, jaeger, honeycomb, opencensus, lightstep, lightstepGrpc, lightstepHttp, log, examples)

lazy val core = project
  .in(file("modules/core"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-core",
    description := "Tagless, non-blocking OpenTracing implementation for Scala.",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core"   % "2.0.0",
      "org.typelevel" %% "cats-effect" % "2.0.0"
    )
  )

lazy val jaeger = project
  .in(file("modules/jaeger"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-jaeger",
    description := "Jaeger support for Natchez.",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.4",
      "io.jaegertracing"        % "jaeger-client"           % "1.2.0",
      "org.slf4j"               % "slf4j-jdk14"             % "1.7.30"
    )
  )

lazy val honeycomb = project
  .in(file("modules/honeycomb"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-honeycomb",
    description := "Honeycomb support for Natchez.",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.4",
      "io.honeycomb.libhoney"   % "libhoney-java"           % "1.1.1"
    )
  )

lazy val opencensus = project
  .in(file("modules/opencensus"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-opencensus",
    description := "Opencensus support for Natchez.",
    libraryDependencies ++= Seq(
      "io.opencensus" % "opencensus-exporter-trace-ocagent" % "0.25.0"
    )
  )

lazy val lightstep = project
  .in(file("modules/lightstep"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name           := "natchez-lightstep",
    description    := "Lightstep support for Natchez.",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.4",
      "com.lightstep.tracer"    % "lightstep-tracer-jre"    % "0.19.0"
    )
  )

lazy val lightstepGrpc = project
  .in(file("modules/lightstep-grpc"))
  .dependsOn(lightstep)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-lightstep-grpc",
    description := "Lightstep gRPC bindings for Natchez.",
    libraryDependencies ++= Seq(
      "com.lightstep.tracer" % "tracer-grpc"                     % "0.20.0",
      "io.grpc"              % "grpc-netty"                      % "1.28.0",
      "io.netty"             % "netty-tcnative-boringssl-static" % "2.0.29.Final"
    )
  )

lazy val lightstepHttp = project
  .in(file("modules/lightstep-http"))
  .dependsOn(lightstep)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-lightstep-http",
    description := "Lightstep HTTP bindings for Natchez.",
    libraryDependencies ++= Seq(
      "com.lightstep.tracer" % "tracer-okhttp" % "0.20.0"
    )
  )

lazy val log = project
  .in(file("modules/log"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-log",
    description := "Logging bindings for Natchez.",
    libraryDependencies ++= Seq(
      "io.circe"          %% "circe-core"    % "0.13.0",
      "io.chrisdavenport" %% "log4cats-core" % "1.0.1",
    )
  )

lazy val examples = project
  .in(file("modules/examples"))
  .dependsOn(core, jaeger, honeycomb, lightstepHttp, log)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    publish / skip       := true,
    name                 := "natchez-examples",
    description          := "Example programs for Natchez.",
    crossScalaVersions  --= List(scala213Version), // until skunk is out for 2.13
    libraryDependencies ++= Seq(
      "io.chrisdavenport" %% "log4cats-slf4j" % "1.0.1",
    )
  )
