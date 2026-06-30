ThisBuild / organization := "org.jetbrains.scala"

lazy val sbtKotlinPlugin = project.in(file("."))
  .enablePlugins(SbtPlugin)
  .settings(Scripted.settings)
  .settings(Publishing.settings)
  .settings(
    name := "sbt-kotlin-plugin",
    crossScalaVersions := Seq("2.12.21"),
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.6.0"
      }
    },
    scalacOptions ++= Seq("-deprecation", "-feature", "-Werror", "-Xlint", "-release", "8"),
    javacOptions  ++= Seq("--release", "8"),
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.3" % Test
  )
