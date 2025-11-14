import org.jetbrains.sbt.kotlin.Keys.*
import sbt.internal.util.ConsoleAppender

import java.io.PrintWriter
import java.nio.file.Files

name := "compiler-error"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "2.2.21"
kotlincJvmTarget := "1.8"

extraAppenders := {
  val existing = extraAppenders.value
  val dir = target.value.toPath.resolve("file-log")
  Files.createDirectories(dir)
  val path = dir.resolve("log.txt")
  val pw = new PrintWriter(Files.newBufferedWriter(path), true)
  val appender = ConsoleAppender.apply(pw)
  (key: ScopedKey[?]) => appender +: existing(key)
}

lazy val assertCompilerError = taskKey[Unit]("Assert compiler errors")

assertCompilerError := {
  // Matches standard ANSI escape sequences, e.g. "\u001b[31m" for red
  val AnsiRegex = """\u001B\[[0-9;]*[ -/]*[@-~]""".r

  def stripAnsiCodes(input: String): String =
    AnsiRegex.replaceAllIn(input, "")

  import scala.jdk.CollectionConverters.*
  val path = target.value.toPath.resolve("file-log").resolve("log.txt")
  val lines = Files.readAllLines(path).asScala.toSeq

  val compilingKotlinSourceMessage = lines.find { message =>
    val nonAnsi = stripAnsiCodes(message)
    nonAnsi.startsWith("[info] compiling 1 Kotlin source to")
  }
  assert(compilingKotlinSourceMessage.isDefined, "Message 'compiling 1 Kotlin source' not found in log")

  val compilerError1 = lines.find { message =>
    val nonAnsi = stripAnsiCodes(message)
    val start = nonAnsi.startsWith("[error]")
    val sep = java.io.File.separator
    val end = nonAnsi.endsWith(s"src${sep}main${sep}kotlin${sep}demo${sep}SimpleError.kt:5:17: Unresolved reference 'name'.")
    start && end
  }
  assert(compilerError1.isDefined, "First compiler error message not found in log")

  val compilerError2 = lines.find { message =>
    val nonAnsi = stripAnsiCodes(message)
    val start = nonAnsi.startsWith("[error]")
    val sep = java.io.File.separator
    val end = nonAnsi.endsWith(s"src${sep}main${sep}kotlin${sep}demo${sep}SimpleError.kt:6:21: Unresolved reference 'age'.")
    start && end
  }
  assert(compilerError2.isDefined, "Second compiler error message not found in log")

  val nonZeroExitCode = lines.find { message =>
    val nonAnsi = stripAnsiCodes(message)
    val start = nonAnsi.startsWith("[error]")
    val sep = java.io.File.separator
    val end = nonAnsi.endsWith(s"kotlinc returned non-zero exit code")
    start && end
  }
  assert(nonZeroExitCode.isDefined, "Non-zero exit code returned by kotlinc message not found in log")
}
