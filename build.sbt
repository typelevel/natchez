ThisBuild / tlBaseVersion := "0.1"

val scala212Version        = "2.12.16"
val scala213Version        = "2.13.8"
val scala30Version         = "3.1.3"

val collectionCompatVersion = "2.8.1"

val catsVersion = "2.8.0"
val catsEffectVersion = "3.3.14"

// Publishing

ThisBuild / organization := "org.tpolecat"
ThisBuild / licenses     := Seq(("MIT", url("http://opensource.org/licenses/MIT")))
ThisBuild / developers   := List(
  Developer("tpolecat", "Rob Norris", "rob_norris@mac.com", url("http://www.tpolecat.org"))
)
ThisBuild / tlSonatypeUseLegacyHost := false

ThisBuild / tlCiReleaseBranches += "series/0.1"

// start MiMa from here
ThisBuild / tlVersionIntroduced := List("2.12", "2.13", "3").map(_ -> "0.1.6").toMap

ThisBuild / githubWorkflowAddedJobs +=
  WorkflowJob(
    id = "docs",
    name = s"Make site",
    scalas = List(scala213Version),
    steps = List(WorkflowStep.CheckoutFull) ++
      WorkflowStep.SetupJava(githubWorkflowJavaVersions.value.toList) ++
      githubWorkflowGeneratedCacheSteps.value ++ 
      List(WorkflowStep.Sbt(List("docs/makeSite")))
  )

// Headers
lazy val commonSettings = Seq(
  headerMappings := headerMappings.value + (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
  headerLicense  := Some(HeaderLicense.Custom(
    """|Copyright (c) 2019-2020 by Rob Norris and Contributors
        |This software is licensed under the MIT License (MIT).
        |For more information see LICENSE or https://opensource.org/licenses/MIT
        |""".stripMargin
    )
  ),
  // Testing
  libraryDependencies ++= Seq(
    "org.scalameta" %%% "munit"             % "1.0.0-M6" % Test,
    "org.scalameta" %%% "munit-scalacheck"  % "1.0.0-M6" % Test,
    "org.typelevel" %%% "munit-cats-effect" % "2.0.0-M3"  % Test,
  )
)

lazy val commonNativeSettings = Seq(
  tlVersionIntroduced := List("2.12", "2.13", "3").map(_ -> "0.1.7").toMap
)

// Compilation
ThisBuild / scalaVersion       := scala213Version
ThisBuild / crossScalaVersions := Seq(scala212Version, scala213Version, scala30Version)

lazy val root = tlCrossRootProject.aggregate(
  core,
  jaeger,
  honeycomb,
  opencensus,
  opentelemetry,
  lightstep, lightstepGrpc, lightstepHttp,
  opentracing,
  datadog,
  log,
  newrelic,
  mtl,
  noop,
  xray,
  logOdin,
  examples
)

lazy val core = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("modules/core"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-core",
    description := "Tagless, non-blocking OpenTracing implementation for Scala.",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core"   % catsVersion,
      "org.typelevel" %%% "cats-effect-kernel" % catsEffectVersion,
      "org.typelevel" %%% "cats-effect" % catsEffectVersion,
    )
  )
  .nativeSettings(commonNativeSettings)

lazy val jaeger = project
  .in(file("modules/jaeger"))
  .dependsOn(core.jvm, opentracing)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-jaeger",
    description := "Jaeger support for Natchez.",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion,
      "io.jaegertracing"        % "jaeger-client"           % "1.8.1",
    )
  )

lazy val honeycomb = project
  .in(file("modules/honeycomb"))
  .dependsOn(core.jvm)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-honeycomb",
    description := "Honeycomb support for Natchez.",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion,
      "io.honeycomb.libhoney"   % "libhoney-java"           % "1.5.2"
    )
  )

lazy val opencensus = project
  .in(file("modules/opencensus"))
  .dependsOn(core.jvm)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-opencensus",
    description := "Opencensus support for Natchez.",
    libraryDependencies ++= Seq(
      "io.opencensus" % "opencensus-exporter-trace-ocagent" % "0.31.1"
    )
  )

lazy val lightstep = project
  .in(file("modules/lightstep"))
  .dependsOn(core.jvm, opentracing)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name           := "natchez-lightstep",
    description    := "Lightstep support for Natchez.",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion,
      "com.lightstep.tracer"    % "lightstep-tracer-jre"    % "0.30.5"
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
      "com.lightstep.tracer" % "tracer-grpc"                     % "0.30.3",
      "io.grpc"              % "grpc-netty"                      % "1.49.0",
      "io.netty"             % "netty-tcnative-boringssl-static" % "2.0.54.Final"
    ),
    mimaPreviousArtifacts := Set()
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
      "com.lightstep.tracer" % "tracer-okhttp" % "0.30.3"
    ),
    mimaPreviousArtifacts := Set()
  )

lazy val opentracing = project
  .in(file("modules/opentracing"))
  .dependsOn(core.jvm)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-opentracing",
    description := "Base OpenTracing Utilities for Natchez",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion,
      "io.opentracing" % "opentracing-api" % "0.33.0" % "provided",
      "io.opentracing" % "opentracing-util" % "0.33.0" % "provided"
    )
  )

lazy val opentelemetry = project
  .in(file("modules/opentelemetry"))
  .dependsOn(core.jvm)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-opentelemetry",
    description := "Base OpenTelemetry Utilities for Natchez",
    tlVersionIntroduced := List("2.12", "2.13", "3").map(_ -> "0.1.7").toMap,
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion,
      "io.opentelemetry"        % "opentelemetry-sdk"       % "1.12.0"
    )
  )



lazy val datadog = project
  .in(file("modules/datadog"))
  .dependsOn(core.jvm, opentracing)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-datadog",
    description := "Datadog bindings for Natchez.",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion,
      "com.datadoghq" % "dd-trace-ot"  % "0.108.1",
      "com.datadoghq" % "dd-trace-api" % "0.108.1"
    )
  )

lazy val log = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("modules/log"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-log",
    description := "Logging bindings for Natchez, using log4cats.",
    libraryDependencies ++= Seq(
      "io.circe"          %%% "circe-core"      % "0.14.3",
      "org.typelevel"     %%% "log4cats-core"   % "2.5.0",
      "io.github.cquiroz" %%% "scala-java-time" % "2.4.0" % Test,
    )
  )
  .nativeSettings(commonNativeSettings)

lazy val newrelic = project
  .in(file("modules/newrelic"))
  .dependsOn(core.jvm)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "newrelic",
    description := "Newrelic bindings for Natchez.",
    libraryDependencies ++= Seq(
      "io.circe"               %% "circe-core"              % "0.14.3",
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion,
      "com.newrelic.telemetry" % "telemetry"                % "0.10.0",
      "com.newrelic.telemetry" % "telemetry-core"           % "0.15.0",
      "com.newrelic.telemetry" % "telemetry-http-okhttp"    % "0.15.0"
    )
  )

lazy val mtl = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("modules/mtl"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-mtl",
    description := "cats-mtl bindings for Natchez.",
    libraryDependencies ++= Seq(
      "org.typelevel"          %%% "cats-mtl"    % "1.3.0",
    )
  )
  .nativeSettings(commonNativeSettings)

lazy val noop = crossProject(JSPlatform, JVMPlatform, NativePlatform)
  .in(file("modules/noop"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-noop",
    description := "No-Op Open Tracing implementation",
    libraryDependencies ++= Seq()
    )
  .nativeSettings(commonNativeSettings)

lazy val xray = crossProject(JSPlatform, JVMPlatform)
  .in(file("modules/xray"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-xray",
    description := "AWS X-Ray bindings implementation",
    libraryDependencies ++= Seq(
      "io.circe"          %%% "circe-core"      % "0.14.3",
      "co.fs2"            %%% "fs2-io"          % "3.3.0",
      "com.comcast"       %%% "ip4s-core"       % "3.1.3",
      "org.scodec"        %%% "scodec-bits"     % "1.1.34"
    )
  )
  .jsSettings(
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  )

lazy val mock = project
  .in(file("modules/mock"))
  .dependsOn(core.jvm)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-mock",
    description := "Mock Open Tracing implementation",
    libraryDependencies ++= Seq(
      "io.opentracing" % "opentracing-mock" % "0.33.0",
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion
    ))


lazy val examples = project
  .in(file("modules/examples"))
  .dependsOn(core.jvm, jaeger, honeycomb, lightstepHttp, datadog, newrelic, log.jvm)
  .enablePlugins(AutomateHeaderPlugin, NoPublishPlugin)
  .settings(commonSettings)
  .settings(
    name                 := "natchez-examples",
    description          := "Example programs for Natchez.",
    scalacOptions        -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      "org.typelevel"     %% "log4cats-slf4j" % "2.5.0",
      "org.slf4j"         %  "slf4j-simple"   % "2.0.0",
      "eu.timepit"        %% "refined"        % "0.9.29",
      "is.cir"            %% "ciris"          % "2.3.3"
    )
  )

lazy val logOdin = project
  .in(file("modules/log-odin"))
  .dependsOn(core.jvm)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-log-odin",
    description := "Logging bindings for Natchez, using Odin.",
    tlVersionIntroduced := List("2.12", "2.13", "3").map(_ -> "0.1.7").toMap,
    libraryDependencies ++= Seq(
      "io.circe"              %% "circe-core" % "0.14.1",
      "com.github.valskalla"  %% "odin-core"  % "0.13.0",
      "com.github.valskalla"  %% "odin-json"  % "0.13.0"
    )
  )

lazy val docs = project
  .in(file("modules/docs"))
  .dependsOn(mtl.jvm, honeycomb, datadog, jaeger, log.jvm)
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(ParadoxPlugin)
  .enablePlugins(ParadoxSitePlugin)
  .enablePlugins(GhpagesPlugin)
  .enablePlugins(MdocPlugin)
  .settings(
    scalacOptions      := Nil,
    git.remoteRepo     := "git@github.com:tpolecat/natchez.git",
    ghpagesNoJekyll    := true,
    publish / skip     := true,
    paradoxTheme       := Some(builtinParadoxTheme("generic")),
    version            := version.value.takeWhile(_ != '+'), // strip off the +3-f22dca22+20191110-1520-SNAPSHOT business
    paradoxProperties ++= Map(
      "scala-versions"            -> (core.jvm / crossScalaVersions).value.map(CrossVersion.partialVersion).flatten.distinct.map { case (a, b) => s"$a.$b"} .mkString("/"),
      "org"                       -> organization.value,
      "scala.binary.version"      -> s"2.${CrossVersion.partialVersion(scalaVersion.value).get._2}",
      "core-dep"                  -> s"${(core.jvm / name).value}_2.${CrossVersion.partialVersion(scalaVersion.value).get._2}",
      "version"                   -> version.value,
      "scaladoc.natchez.base_url" -> s"https://static.javadoc.io/org.tpolecat/natchez-core_2.13/${version.value}",
    ),
    mdocIn := (baseDirectory.value) / "src" / "main" / "paradox",
    Compile / paradox / sourceDirectory := mdocOut.value,
    makeSite := makeSite.dependsOn(mdoc.toTask("")).value,
    mdocExtraArguments := Seq("--no-link-hygiene"), // paradox handles this
    libraryDependencies ++= Seq(
      "org.http4s"    %% "http4s-dsl"     % "0.23.15",
      "org.http4s"    %% "http4s-client"  % "0.23.15",
      "org.typelevel" %% "log4cats-slf4j" % "2.4.0",
      "org.slf4j"     %  "slf4j-simple"   % "2.0.0",
    ),
    excludeDependencies += "org.scala-lang.modules" % "scala-collection-compat_3", // pray this does more good than harm
  )
