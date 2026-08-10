// This is a special plugin applied here, not in `project/plugins.sbt` because it needs to be available
// to our sbt-kotlin-plugin's sources, not the meta-build.
addSbtPlugin("com.github.sbt" % "sbt2-compat" % "0.1.0")

ThisBuild / organization := "org.jetbrains.scala"

lazy val sbtKotlinPlugin = project.in(file("."))
  .enablePlugins(SbtPlugin)
  .settings(Scripted.settings)
  .settings(Publishing.settings)
  .settings(
    name := "sbt-kotlin-plugin",
    crossScalaVersions := Seq("2.12.21", "3.8.4"),
    pluginCrossBuild / sbtVersion := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.6.0"
        case "3" => "2.0.0"
      }
    },
    scalacOptions ++= {
      val common = Seq("-deprecation", "-feature", "-Werror")
      val versionSpecific = scalaBinaryVersion.value match {
        case "2.12" => Seq("-Xlint", "-release", "8")
        case "3" => Seq(
          "-release", "17",
          "-Wconf:msg=method reflectiveSelectableFromLangReflectiveCalls in object Selectable is deprecated:s"
        )
      }
      common ++ versionSpecific
    },
    javacOptions ++= {
      scalaBinaryVersion.value match {
        case "2.12" => Seq("-release", "8")
        case "3" => Seq("-release", "17")
      }
    },
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.5" % Test,
    scriptedSbt := {
      scalaBinaryVersion.value match {
        case "2.12" => "1.12.15"
        case "3" => "2.0.6"
      }
    }
  )
