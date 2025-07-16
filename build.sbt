ThisBuild / organization := "org.jetbrains.scala"

lazy val sbtKotlinPlugin = project.in(file("."))
  .enablePlugins(SbtPlugin)
  .settings(Scripted.settings)
  .settings(Publishing.settings)
  .settings(
    name := "sbt-kotlin-plugin",

    scalaVersion   := "2.12.20",
    scalacOptions ++= Seq("-deprecation", "-feature", "-Werror", "-Xlint", "-release", "8"),
    javacOptions  ++= Seq("--release", "8"),

    libraryDependencies += "org.scalameta" %% "munit" % "1.0.2" % Test,
  )
