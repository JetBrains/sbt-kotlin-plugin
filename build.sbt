lazy val sbtKotlinPlugin = project.in(file("."))
  .enablePlugins(SbtPlugin)
  .settings(Scripted.settings)
  .settings(Publishing.settings)
  .settings(
    name         := "sbt-kotlin-plugin",
    organization := "org.jetbrains.scala",
    licenses     += ("MIT", url("https://opensource.org/license/mit/")),

    scalaVersion   := "2.12.17",
    scalacOptions ++= Seq("-deprecation", "-feature", "-Werror", "-Xlint", "-release", "8"),
    javacOptions  ++= Seq("--release", "8"),
  )
