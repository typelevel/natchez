lazy val scala212Version = "2.12.12"
lazy val scala213Version = "2.13.3"

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
    """|Copyright (c) 2019-2020 by Rob Norris and Contributors
       |This software is licensed under the MIT License (MIT).
       |For more information see LICENSE or https://opensource.org/licenses/MIT
       |""".stripMargin
    )
  ),

  // Compilation
  scalaVersion       := scala213Version,
  crossScalaVersions := Seq(scala212Version, scala213Version),
  Compile / console / scalacOptions --= Seq("-Xfatal-warnings", "-Ywarn-unused:imports"),
  Compile / doc     / scalacOptions --= Seq("-Xfatal-warnings"),
  Compile / doc     / scalacOptions ++= Seq(
    "-groups",
    "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath,
    "-doc-source-url", "https://github.com/tpolecat/natchez/blob/v" + version.value + "€{FILE_PATH}.scala"
  ),
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full),

)

lazy val natchez = project
  .in(file("."))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    crossScalaVersions := Nil,
    publish / skip     := true
  )
  .dependsOn(core, jaeger, honeycomb, opencensus, datadog, lightstep, lightstepGrpc, lightstepHttp, log, noop, mock, newrelic, examples)
  .aggregate(core, jaeger, honeycomb, opencensus, datadog, lightstep, lightstepGrpc, lightstepHttp, log, noop, mock, newrelic, examples)

lazy val core = project
  .in(file("modules/core"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-core",
    description := "Tagless, non-blocking OpenTracing implementation for Scala.",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core"   % "2.2.0",
      "org.typelevel" %% "cats-effect" % "2.2.0"
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
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.6",
      "io.jaegertracing"        % "jaeger-client"           % "1.4.0",
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
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.6",
      "io.honeycomb.libhoney"   % "libhoney-java"           % "1.2.0"
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
      "io.opencensus" % "opencensus-exporter-trace-ocagent" % "0.28.1"
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
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.6",
      "com.lightstep.tracer"    % "lightstep-tracer-jre"    % "0.30.1"
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
      "com.lightstep.tracer" % "tracer-grpc"                     % "0.30.0",
      "io.grpc"              % "grpc-netty"                      % "1.31.1",
      "io.netty"             % "netty-tcnative-boringssl-static" % "2.0.34.Final"
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
      "com.lightstep.tracer" % "tracer-okhttp" % "0.30.0"
    )
  )

lazy val datadog = project
  .in(file("modules/datadog"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-datadog",
    description := "Lightstep HTTP bindings for Natchez.",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.6",
      "com.datadoghq" % "dd-trace-ot"  % "0.61.0",
      "com.datadoghq" % "dd-trace-api" % "0.61.0"
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
      "io.chrisdavenport" %% "log4cats-core" % "1.1.1",
    )
  )

lazy val newrelic = project
  .in(file("modules/newrelic"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "newrelic",
    description := "Newrelic bindings for Natchez.",
    libraryDependencies ++= Seq(
      "io.circe"               %% "circe-core"              % "0.13.0",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.4",
      "com.newrelic.telemetry" % "telemetry"                % "0.4.0",
      "com.newrelic.telemetry" % "telemetry-core"           % "0.4.0",
      "com.newrelic.telemetry" % "telemetry-http-okhttp"    % "0.4.0"
    )
  )

lazy val noop = project
  .in(file("modules/noop"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-noop",
    description := "No-Op Open Tracing implementation",
    libraryDependencies ++= Seq()
    )

lazy val mock = project
  .in(file("modules/mock"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-mock",
    description := "Mock Open Tracing implementation",
    libraryDependencies ++= Seq(
      "io.opentracing" % "opentracing-mock" % "0.33.0",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.4"
    ))


lazy val examples = project
  .in(file("modules/examples"))
  .dependsOn(core, jaeger, honeycomb, lightstepHttp, datadog, log, newrelic)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    publish / skip       := true,
    name                 := "natchez-examples",
    description          := "Example programs for Natchez.",
    libraryDependencies ++= Seq(
      "io.chrisdavenport" %% "log4cats-slf4j" % "1.1.1",
      "org.slf4j"         % "slf4j-simple"    % "1.7.30",
      "eu.timepit"        %% "refined"        % "0.9.17",
      "is.cir"            %% "ciris"          % "1.2.1"
    )
  )
