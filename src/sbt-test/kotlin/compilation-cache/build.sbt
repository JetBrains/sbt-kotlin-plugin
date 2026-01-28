import org.jetbrains.sbt.kotlin.Keys.*
import sbt.internal.util.ConsoleAppender

import java.io.PrintWriter
import java.nio.file.Files

name := "compilation-cache"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "2.2.21"
kotlincJvmTarget := "1.8"

libraryDependencies += "com.github.sbt" % "junit-interface" % "0.13.3" % Test

ThisBuild / pushRemoteCacheTo := {
  val tmpDir = Files.createTempDirectory("compilation-cache-").toRealPath()
  sLog.value.info(tmpDir.toString)
  Some(MavenCache("compilation-cache", tmpDir.toFile))
}
