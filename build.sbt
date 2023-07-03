name := "sbt-kotlin-plugin"

organization := "org.jetbrains.scala"

version := "3.0.0-SNAPSHOT"

scalacOptions ++= Seq("-deprecation","-Xlint","-feature")

sbtPlugin := true

// build info plugin

enablePlugins(BuildInfoPlugin, SbtPlugin)

buildInfoPackage := "kotlin"

// bintray
bintrayRepository := "sbt-plugins"

publishMavenStyle := false

licenses += ("MIT", url("https://opensource.org/license/mit/"))

bintrayOrganization := None

// scripted
scriptedLaunchOpts ++= Seq(
  "-Xmx1024m",
  "-Dplugin.version=" + version.value
)
