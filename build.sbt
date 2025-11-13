ThisBuild / organization := "org.jetbrains.scala"

lazy val sbtKotlinPlugin = project.in(file("."))
  .enablePlugins(SbtPlugin)
  .settings(Scripted.settings)
  .settings(Publishing.settings)
  .settings(
    name := "sbt-kotlin-plugin",
    crossScalaVersions := Seq("2.12.20", "3.7.4"),
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.11.7"
        case "3" => "2.0.0-RC6"
      }
    },

    scalacOptions ++= {
      scalaBinaryVersion.value match {
        case "2.12" => Seq("-deprecation", "-feature", "-Werror", "-Xlint", "-release", "8")
        case "3" => Seq("-deprecation", "-feature", "--release", "8")
      }
    },
    javacOptions  ++= Seq("--release", "8"),

    libraryDependencies += "org.scalameta" %% "munit" % "1.2.1" % Test
  )
