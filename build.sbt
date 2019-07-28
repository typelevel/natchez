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
  scalaVersion       := "2.12.8",
  crossScalaVersions := Seq("2.11.12", scalaVersion.value),
  Compile / console / scalacOptions --= Seq("-Xfatal-warnings", "-Ywarn-unused:imports"),
  Compile / doc     / scalacOptions --= Seq("-Xfatal-warnings"),
  Compile / doc     / scalacOptions ++= Seq(
    "-groups",
    "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath,
    "-doc-source-url", "https://github.com/tpolecat/natchez/blob/v" + version.value + "€{FILE_PATH}.scala",
  ),
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.10"),

)

lazy val natchez = project
  .in(file("."))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(publish / skip := true)
  .dependsOn(core, jaeger, honeycomb, opencensus, lightstep, examples)
  .aggregate(core, jaeger, honeycomb, opencensus, lightstep, examples)

lazy val core = project
  .in(file("modules/core"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-core",
    description := "Tagless, non-blocking OpenTracing implementation for Scala.",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core"       % "1.6.0",
      "org.typelevel" %% "cats-effect"     % "1.3.0",
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
      "io.jaegertracing" % "jaeger-client" % "0.35.5",
      "org.slf4j"        % "slf4j-jdk14"   % "1.7.26",
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
      "io.honeycomb.libhoney" % "libhoney-java" % "1.0.5",
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
      "io.opencensus" % "opencensus-exporter-trace-ocagent" % "0.20.0",
    )
  )

lazy val lightstep = project
  .in(file("modules/lightstep"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name        := "natchez-lightstep",
    description := "Lightstep support for Natchez.",
    libraryDependencies ++= Seq(
      "com.lightstep.tracer" % "lightstep-tracer-jre"            % "0.16.4",
      "com.lightstep.tracer" % "tracer-grpc"                     % "0.17.2",
      "io.grpc"              % "grpc-netty"                      % "1.20.0",
      "io.netty"             % "netty-tcnative-boringssl-static" % "2.0.25.Final",
    )
  )

lazy val examples = project
  .in(file("modules/examples"))
  .dependsOn(core, jaeger, honeycomb)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    publish / skip := true,
    name           := "natchez-examples",
    description    := "Example programs for Natchez.",
    libraryDependencies ++= Seq(
      "org.tpolecat"    %% "skunk-core"    % "0.0.3"
    )
  )
