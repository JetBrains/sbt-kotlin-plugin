ThisBuild / organization := "org.jetbrains.scala"

// Optional but nice-to-have
ThisBuild / organizationName     := "JetBrains"
ThisBuild / organizationHomepage := Some(url("https://www.jetbrains.com/"))

ThisBuild / licenses += ("MIT", url("https://opensource.org/license/mit/"))

ThisBuild / homepage := Some(url("https://github.com/JetBrains/sbt-kotlin-plugin"))

ThisBuild / developers := List(
  Developer(
    id    = "JetBrains",
    name  = "JetBrains",
    email = "scala-developers@jetbrains.com",
    url   = url("https://github.com/JetBrains")
  )
)

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
