// https://github.com/sbt/sbt/issues/6997
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

addSbtPlugin("org.typelevel" % "sbt-typelevel" % "0.7.1")
addSbtPlugin("org.typelevel" % "sbt-typelevel-mergify" % "0.7.1")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox" % "0.10.7")
addSbtPlugin("com.github.sbt" % "sbt-site" % "1.6.0")
addSbtPlugin("com.github.sbt" % "sbt-site-paradox" % "1.6.0")
addSbtPlugin("com.github.sbt" % "sbt-ghpages" % "0.8.0")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.6.4")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.16.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.5")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.5.3")
