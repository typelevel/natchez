val scala212Version        = "2.12.15"
val scala213Version        = "2.13.8"
val scala30Version         = "3.1.2"

val collectionCompatVersion = "2.6.0"

val catsVersion = "2.7.0"
val catsEffectVersion = "3.3.12"

// We do `evictionCheck` in CI and don't sweat the Java deps for now.
inThisBuild(Seq(
  evictionRules ++= Seq(
    "io.netty"               % "*" % "always",
    "io.grpc"                % "*" % "always",
    "com.github.jnr"         % "*" % "always",
    "com.google.guava"       % "*" % "always",
    "io.opentracing"         % "*" % "always",
    "io.opentracing.contrib" % "*" % "always",
    "com.squareup.okhttp3"   % "*" % "always",
    "com.squareup.okio"      % "*" % "always",
    "com.newrelic.telemetry" % "*" % "always",
    "org.typelevel"          % "*" % "semver-spec",
    "org.scala-js"           % "*" % "semver-spec",
    "org.jctools"            % "*" % "always",
    "org.jetbrains"          % "*" % "always",
    "org.jboss.logging"      % "*" % "always",
    "org.jboss.threads"      % "*" % "always",
    "org.wildfly.common"     % "*" % "always",
    "org.jboss.xnio"         % "*" % "always",
    "com.lihaoyi"            % "*" % "always",
  )
))

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

  // Testing
  libraryDependencies ++= Seq(
    "org.scalameta" %%% "munit"               % "0.7.29" % Test,
    "org.scalameta" %%% "munit-scalacheck"    % "0.7.29" % Test,
    "org.typelevel" %%% "munit-cats-effect-3" % "1.0.7"  % Test,
  ),
  testFrameworks += new TestFramework("munit.Framework"),

  // Compilation
  scalaVersion       := scala213Version,
  crossScalaVersions := Seq(scala212Version, scala213Version, scala30Version),
  Compile / console / scalacOptions --= Seq("-Xfatal-warnings", "-Ywarn-unused:imports"),
  Compile / doc     / scalacOptions --= Seq("-Xfatal-warnings"),
  Compile / doc     / scalacOptions ++= Seq(
    "-groups",
    "-sourcepath", (LocalRootProject / baseDirectory).value.getAbsolutePath,
    "-doc-source-url", "https://github.com/tpolecat/natchez/blob/v" + version.value + "€{FILE_PATH}.scala"
  ),
  libraryDependencies ++= Seq(
    compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
  ).filterNot(_ => scalaVersion.value.startsWith("3.")),

  // dottydoc really doesn't work at all right now
  Compile / doc / sources := {
    val old = (Compile / doc / sources).value
    if (scalaVersion.value.startsWith("3."))
      Seq()
    else
      old
  },

)

// root project
commonSettings
publish / skip := true

lazy val crossProjectSettings = Seq(
  Compile / unmanagedSourceDirectories ++= {
    val major = if (scalaVersion.value.startsWith("3.")) "-3" else "-2"
    List(CrossType.Pure, CrossType.Full).flatMap(
      _.sharedSrcDir(baseDirectory.value, "main").toList.map(f => file(f.getPath + major)))
  },

  Test / unmanagedSourceDirectories ++= {
    val major = if (scalaVersion.value.startsWith("3.")) "-3" else "-2"
    List(CrossType.Pure, CrossType.Full).flatMap(
      _.sharedSrcDir(baseDirectory.value, "test").toList.map(f => file(f.getPath + major)))
  },
)

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
      "org.typelevel" %%% "cats-effect-kernel" % catsEffectVersion,
      "org.typelevel" %%% "cats-effect" % catsEffectVersion,
    )
  )

lazy val coreJVM = core.jvm
lazy val coreJS = core.js
  .settings(
    Test / scalaJSStage := FastOptStage,
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  )

lazy val jaeger = project
  .in(file("modules/jaeger"))
  .dependsOn(coreJVM, opentracing)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-jaeger",
    description := "Jaeger support for Natchez.",
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion,
      "io.jaegertracing"        % "jaeger-client"           % "1.6.0",
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
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion,
      "io.honeycomb.libhoney"   % "libhoney-java"           % "1.4.1"
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
  .dependsOn(coreJVM, opentracing)
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
      "io.grpc"              % "grpc-netty"                      % "1.47.0",
      "io.netty"             % "netty-tcnative-boringssl-static" % "2.0.46.Final"
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
      "com.lightstep.tracer" % "tracer-okhttp" % "0.30.3"
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
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion,
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
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion,
      "com.datadoghq" % "dd-trace-ot"  % "0.91.0",
      "com.datadoghq" % "dd-trace-api" % "0.91.0"
    )
  )

lazy val log = crossProject(JSPlatform, JVMPlatform)
  .in(file("modules/log"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(crossProjectSettings)
  .settings(
    name        := "natchez-log",
    description := "Logging bindings for Natchez, using log4cats.",
    libraryDependencies ++= Seq(
      "io.circe"          %%% "circe-core"      % "0.14.1",
      "org.typelevel"     %%% "log4cats-core"   % "2.1.1",
      "io.github.cquiroz" %%% "scala-java-time" % "2.3.0" % Test,
    )
  )
lazy val logJVM = log.jvm.dependsOn(coreJVM)
lazy val logJS = log.js.dependsOn(coreJS)
  .settings(
    Test / scalaJSStage := FastOptStage,
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  )

lazy val newrelic = project
  .in(file("modules/newrelic"))
  .dependsOn(coreJVM)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "newrelic",
    description := "Newrelic bindings for Natchez.",
    libraryDependencies ++= Seq(
      "io.circe"               %% "circe-core"              % "0.14.1",
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion,
      "com.newrelic.telemetry" % "telemetry"                % "0.10.0",
      "com.newrelic.telemetry" % "telemetry-core"           % "0.12.0",
      "com.newrelic.telemetry" % "telemetry-http-okhttp"    % "0.12.0"
    )
  )

lazy val mtl = crossProject(JSPlatform, JVMPlatform)
  .in(file("modules/mtl"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-mtl",
    description := "cats-mtl bindings for Natchez.",
    libraryDependencies ++= Seq(
      "org.typelevel"          %%% "cats-mtl"    % "1.2.1",
    )
  )

lazy val mtlJVM = mtl.jvm.dependsOn(coreJVM)
lazy val mtlJS = mtl.js.dependsOn(coreJS)
  .settings(
    Test / scalaJSStage := FastOptStage,
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  )

lazy val noop = crossProject(JSPlatform, JVMPlatform)
  .in(file("modules/noop"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-noop",
    description := "No-Op Open Tracing implementation",
    libraryDependencies ++= Seq()
    )
  .jsSettings(
    Test / scalaJSStage := FastOptStage,
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  )

lazy val xray = crossProject(JSPlatform, JVMPlatform)
  .in(file("modules/xray"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(crossProjectSettings)
  .settings(
    name        := "natchez-xray",
    description := "AWS X-Ray bindings implementation",
    libraryDependencies ++= Seq(
      "io.circe"          %%% "circe-core"      % "0.14.1",
      "co.fs2"            %%% "fs2-io"          % "3.2.7",
      "com.comcast"       %%% "ip4s-core"       % "3.1.3",
      "org.scodec"        %%% "scodec-bits"     % "1.1.31"
    )
  )
  .jsSettings(
    Test / scalaJSStage := FastOptStage,
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
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
      "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatVersion
    ))


lazy val examples = project
  .in(file("modules/examples"))
  .dependsOn(coreJVM, jaeger, honeycomb, lightstepHttp, datadog, newrelic, logJVM)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    publish / skip       := true,
    name                 := "natchez-examples",
    description          := "Example programs for Natchez.",
    scalacOptions        -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      "org.typelevel"     %% "log4cats-slf4j" % "2.1.1",
      "org.slf4j"         %  "slf4j-simple"   % "1.7.36",
      "eu.timepit"        %% "refined"        % "0.9.28",
      "is.cir"            %% "ciris"          % "2.3.2"
    )
  )

// lazy val logOdin = project
//   .in(file("modules/log-odin"))
//   .dependsOn(coreJVM)
//   .enablePlugins(AutomateHeaderPlugin)
//   .settings(commonSettings)
//   .settings(
//     publish / skip := scalaVersion.value.startsWith("3."),
//     name        := "natchez-log-odin",
//     description := "Logging bindings for Natchez, using Odin.",
//     libraryDependencies ++= Seq(
//       "io.circe"              %% "circe-core" % "0.14.0",
//       "com.github.valskalla"  %% "odin-core"  % "0.9.1",
//       "com.github.valskalla"  %% "odin-json"  % "0.9.1"
//     ).filterNot(_ => scalaVersion.value.startsWith("3."))
//   )

lazy val docs = project
  .in(file("modules/docs"))
  .dependsOn(mtlJVM, honeycomb, datadog, jaeger, logJVM)
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(ParadoxPlugin)
  .enablePlugins(ParadoxSitePlugin)
  .enablePlugins(GhpagesPlugin)
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(
    scalacOptions      := Nil,
    git.remoteRepo     := "git@github.com:tpolecat/natchez.git",
    ghpagesNoJekyll    := true,
    publish / skip     := true,
    paradoxTheme       := Some(builtinParadoxTheme("generic")),
    version            := version.value.takeWhile(_ != '+'), // strip off the +3-f22dca22+20191110-1520-SNAPSHOT business
    paradoxProperties ++= Map(
      "scala-versions"            -> (coreJVM / crossScalaVersions).value.map(CrossVersion.partialVersion).flatten.distinct.map { case (a, b) => s"$a.$b"} .mkString("/"),
      "org"                       -> organization.value,
      "scala.binary.version"      -> s"2.${CrossVersion.partialVersion(scalaVersion.value).get._2}",
      "core-dep"                  -> s"${(coreJVM / name).value}_2.${CrossVersion.partialVersion(scalaVersion.value).get._2}",
      "version"                   -> version.value,
      "scaladoc.natchez.base_url" -> s"https://static.javadoc.io/org.tpolecat/natchez-core_2.13/${version.value}",
    ),
    mdocIn := (baseDirectory.value) / "src" / "main" / "paradox",
    Compile / paradox / sourceDirectory := mdocOut.value,
    makeSite := makeSite.dependsOn(mdoc.toTask("")).value,
    mdocExtraArguments := Seq("--no-link-hygiene"), // paradox handles this
    libraryDependencies ++= Seq(
      "org.http4s"    %% "http4s-dsl"     % "0.23.7",
      "org.http4s"    %% "http4s-client"  % "0.23.7",
      "org.typelevel" %% "log4cats-slf4j" % "2.1.1",
      "org.slf4j"     %  "slf4j-simple"   % "1.7.36",
    ),
    excludeDependencies += "org.scala-lang.modules" % "scala-collection-compat_3", // pray this does more good than harm
  )
