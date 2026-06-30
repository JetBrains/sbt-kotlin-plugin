import org.jetbrains.sbt.kotlin.Keys.*
import sbt.internal.util.ConsoleAppender

import java.io.PrintWriter
import java.nio.file.{Files, Paths}

name := "incremental"

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

@transient
lazy val backupClasses = taskKey[Unit]("Backup compiled class files")

backupClasses := {
  val converter = fileConverter.value
  val outputDir = converter.toPath((Compile / backendOutput).value)

  val toBackup = Seq(
    outputDir.resolve("demo").resolve("Person.class"),
    outputDir.resolve("demo").resolve("MainKt.class")
  )

  val backupClassesDir = Paths.get("backup-classes")
  Files.createDirectories(backupClassesDir)
  toBackup.foreach { path => Files.copy(path, backupClassesDir.resolve(path.getFileName)) }
}

@transient
lazy val assertClassesContents = taskKey[Unit]("Assert compiled classes contents")

assertClassesContents := {
  val converter = fileConverter.value
  val outputDir = converter.toPath((Compile / backendOutput).value)

  val toCheck = Seq(
    (Paths.get("demo", "Person.class"), true),
    (Paths.get("demo", "MainKt.class"), false)
  )

  val backupClassesDir = Paths.get("backup-classes")
  toCheck.foreach { case (path, shouldEqual) =>
    val bytes1 = Files.readAllBytes(backupClassesDir.resolve(path.getFileName))
    val bytes2 = Files.readAllBytes(outputDir.resolve(path))
    if (shouldEqual) {
      assert(java.util.Arrays.equals(bytes1, bytes2), s"The contents of the output file ${path} have changed")
    } else {
      assert(!java.util.Arrays.equals(bytes1, bytes2), s"The contents of the output file ${path} have not changed")
    }
  }
}
