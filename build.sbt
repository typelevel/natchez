
val scala212Version        = "2.12.12"
val scala213Version        = "2.13.4"
val scala30PreviousVersion = "3.0.0-M3"
val scala30Version         = "3.0.0-RC1"

val catsVersion = "2.4.2"
val catsEffectVersion = "2.3.3"

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
  crossScalaVersions := Seq(scala212Version, scala213Version, scala30PreviousVersion, scala30Version),
  Compile / console / scalacOptions --= Seq("-Xfatal-warnings", "-Ywarn-unused:imports"),
  Compile / doc     / scalacOptions --= Seq("-Xfatal-warnings"),
  Compile / doc     / scalacOptions ++= Seq(
    "-groups",
    "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath,
    "-doc-source-url", "https://github.com/tpolecat/natchez/blob/v" + version.value + "€{FILE_PATH}.scala"
  ),
  libraryDependencies ++= Seq(
    compilerPlugin("org.typelevel" %% "kind-projector" % "0.11.3" cross CrossVersion.full),
  ).filterNot(_ => isDotty.value),

  // Add some more source directories
  unmanagedSourceDirectories in Compile ++= {
    val sourceDir = (sourceDirectory in Compile).value
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _))  => Seq(sourceDir / "scala-3")
      case Some((2, _))  => Seq(sourceDir / "scala-2")
      case _             => Seq()
    }
  },

  // Also for test
  unmanagedSourceDirectories in Test ++= {
    val sourceDir = (sourceDirectory in Test).value
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _))  => Seq(sourceDir / "scala-3")
      case Some((2, _))  => Seq(sourceDir / "scala-2")
      case _             => Seq()
    }
  },

  // dottydoc really doesn't work at all right now
  Compile / doc / sources := {
    val old = (Compile / doc / sources).value
    if (isDotty.value)
      Seq()
    else
      old
  },

)

lazy val crossProjectSettings = Seq(
  Compile / unmanagedSourceDirectories ++= {
    val major = if (isDotty.value) "-3" else "-2"
    List(CrossType.Pure, CrossType.Full).flatMap(
      _.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + major)))
  },

  Test / unmanagedSourceDirectories ++= {
    val major = if (isDotty.value) "-3" else "-2"
    List(CrossType.Pure, CrossType.Full).flatMap(
      _.sharedSrcDir(baseDirectory.value, "test").toList.map(f => file(f.getPath + major)))
  },
)

lazy val natchez = project
  .in(file("."))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    crossScalaVersions := Nil,
    publish / skip     := true
  )
  .dependsOn(coreJS, coreJVM, jaeger, honeycomb, opencensus, datadog, lightstep, lightstepGrpc, lightstepHttp, logJS, logJVM, mtlJS, mtlJVM, noop, mock, newrelic, logOdin, examples)
  .aggregate(coreJS, coreJVM, jaeger, honeycomb, opencensus, datadog, lightstep, lightstepGrpc, lightstepHttp, logJS, logJVM, mtlJS, mtlJVM, noop, mock, newrelic, logOdin, examples)

lazy val core = crossProject(JSPlatform, JVMPlatform)
  .in(file("modules/core"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(crossProjectSettings)
  .settings(
    name        := "natchez-core",
    description := "Tagless, non-blocking OpenTracing implementation for Scala.",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core"   % catsVersion,
      "org.typelevel" %%% "cats-effect" % catsEffectVersion,
    )
  )

lazy val coreJVM = core.jvm
lazy val coreJS = core.js
  .settings(
    scalaJSStage in Test := FastOptStage,
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  )

lazy val jaeger = project
  .in(file("modules/jaeger"))
  .dependsOn(coreJVM)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-jaeger",
    description := "Jaeger support for Natchez.",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.2",
      "io.jaegertracing"        % "jaeger-client"           % "1.5.0",
    )
  )

lazy val honeycomb = project
  .in(file("modules/honeycomb"))
  .dependsOn(coreJVM)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-honeycomb",
    description := "Honeycomb support for Natchez.",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.2",
      "io.honeycomb.libhoney"   % "libhoney-java"           % "1.3.1"
    )
  )

lazy val opencensus = project
  .in(file("modules/opencensus"))
  .dependsOn(coreJVM)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-opencensus",
    description := "Opencensus support for Natchez.",
    libraryDependencies ++= Seq(
      "io.opencensus" % "opencensus-exporter-trace-ocagent" % "0.28.3"
    )
  )

lazy val lightstep = project
  .in(file("modules/lightstep"))
  .dependsOn(coreJVM)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name           := "natchez-lightstep",
    description    := "Lightstep support for Natchez.",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.2",
      "com.lightstep.tracer"    % "lightstep-tracer-jre"    % "0.30.3"
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
      "com.lightstep.tracer" % "tracer-grpc"                     % "0.30.1",
      "io.grpc"              % "grpc-netty"                      % "1.35.0",
      "io.netty"             % "netty-tcnative-boringssl-static" % "2.0.36.Final"
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
      "com.lightstep.tracer" % "tracer-okhttp" % "0.30.1"
    )
  )

lazy val opentracing = project
  .in(file("modules/opentracing"))
  .dependsOn(coreJVM)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-opentracing",
    description := "Base OpenTracing Utilities for Natchez",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.2",
      "io.opentracing" % "opentracing-api" % "0.33.0" % "provided",
      "io.opentracing" % "opentracing-util" % "0.33.0" % "provided"
    )
  )


lazy val datadog = project
  .in(file("modules/datadog"))
  .dependsOn(coreJVM, opentracing)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-datadog",
    description := "Datadog bindings for Natchez.",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.2",
      "com.datadoghq" % "dd-trace-ot"  % "0.72.0",
      "com.datadoghq" % "dd-trace-api" % "0.72.0"
    )
  )

lazy val log = crossProject(JSPlatform, JVMPlatform)
  .in(file("modules/log"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(crossProjectSettings)
  .settings(
    publish / skip := isDotty.value,
    name        := "natchez-log",
    description := "Logging bindings for Natchez, using log4cats.",
    libraryDependencies ++= Seq(
      "io.circe"          %%% "circe-core"    % "0.13.0",
      "io.chrisdavenport" %%% "log4cats-core" % "1.1.1",
    ).filterNot(_ => isDotty.value)
  )
lazy val logJVM = log.jvm.dependsOn(coreJVM)
lazy val logJS = log.js.dependsOn(coreJS)
  .settings(
    scalaJSStage in Test := FastOptStage,
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  )

lazy val newrelic = project
  .in(file("modules/newrelic"))
  .dependsOn(coreJVM)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    publish / skip := isDotty.value,
    name        := "newrelic",
    description := "Newrelic bindings for Natchez.",
    libraryDependencies ++= Seq(
      "io.circe"               %% "circe-core"              % "0.13.0",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.2",
      "com.newrelic.telemetry" % "telemetry"                % "0.10.0",
      "com.newrelic.telemetry" % "telemetry-core"           % "0.11.0",
      "com.newrelic.telemetry" % "telemetry-http-okhttp"    % "0.11.0"
    ).filterNot(_ => isDotty.value)
  )

lazy val mtl = crossProject(JSPlatform, JVMPlatform)
  .in(file("modules/mtl"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-mtl",
    description := "cats-mtl bindings for Natchez.",
    libraryDependencies ++= Seq(
      "org.typelevel"          %%% "cats-mtl"    % "1.1.2",
    )
  )

lazy val mtlJVM = mtl.jvm.dependsOn(coreJVM)
lazy val mtlJS = mtl.js.dependsOn(coreJS)
  .settings(
    scalaJSStage in Test := FastOptStage,
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  )

lazy val noop = project
  .in(file("modules/noop"))
  .dependsOn(coreJVM)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-noop",
    description := "No-Op Open Tracing implementation",
    libraryDependencies ++= Seq()
    )

lazy val mock = project
  .in(file("modules/mock"))
  .dependsOn(coreJVM)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-mock",
    description := "Mock Open Tracing implementation",
    libraryDependencies ++= Seq(
      "io.opentracing" % "opentracing-mock" % "0.33.0",
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.4.2"
    ))


lazy val examples = project
  .in(file("modules/examples"))
  .dependsOn(coreJVM, jaeger, honeycomb, lightstepHttp, datadog, logJVM, newrelic, logOdin)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    publish / skip       := true,
    name                 := "natchez-examples",
    description          := "Example programs for Natchez.",
    scalacOptions        -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      "io.chrisdavenport" %% "log4cats-slf4j" % "1.1.1",
      "org.slf4j"         % "slf4j-simple"    % "1.7.30",
      "eu.timepit"        %% "refined"        % "0.9.21",
      "is.cir"            %% "ciris"          % "1.2.1"
    ).filterNot(_ => isDotty.value)
  )

lazy val logOdin = project
  .in(file("modules/log-odin"))
  .dependsOn(coreJVM)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    publish / skip := isDotty.value,
    name        := "natchez-log-odin",
    description := "Logging bindings for Natchez, using Odin.",
    libraryDependencies ++= Seq(
      "io.circe"              %% "circe-core" % "0.13.0",
      "com.github.valskalla"  %% "odin-core"  % "0.9.1",
      "com.github.valskalla"  %% "odin-json"  % "0.9.1"
    ).filterNot(_ => isDotty.value)
  )
