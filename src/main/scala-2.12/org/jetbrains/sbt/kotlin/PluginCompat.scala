package org.jetbrains.sbt.kotlin

import org.jetbrains.sbt.kotlin.Keys.*
import sbt.*
import sbt.Keys.*
import xsbti.FileConverter
import xsbti.compile.CompileResult

import java.nio.file.Path as NioPath
import scala.annotation.unused
import scala.language.higherKinds

private[kotlin] object PluginCompat {
  type FileRef = File

  def toNioPath(fileRef: FileRef, @unused converter: FileConverter): NioPath = fileRef.toPath

  def toNioPaths(cp: Seq[Attributed[File]], @unused converter: FileConverter): Seq[NioPath] =
    cp.map(_.data.toPath())

  def managedJarsCompat(config: Configuration, jarTypes: Set[String], up: UpdateReport, @unused converter: FileConverter): Classpath =
    Classpaths.managedJars(config, jarTypes, up)

  implicit def seqDerivedOrdering[CC[X] <: scala.collection.Seq[X], T](implicit ord: Ordering[T]): Ordering[CC[T]] =
    scala.math.Ordering.Implicits.seqDerivedOrdering

  val compileIncrementalTaskImpl: Def.Initialize[Task[CompileResult]] = Def.task {
    KotlinCompile.compileIncremental(
      streams = streams.value,
      inputs = (compile / compileInputs).value,
      converter = fileConverter.value,
      kotlinVersion = kotlinVersion.value,
      kotlincOptions = kotlincOptions.value,
      kotlincJvmTarget = kotlincJvmTarget.value,
      kotlinModuleName = kotlinModuleName.value,
      kotlincPluginOptions = kotlincPluginOptions.value,
      classpathOptions = classpathOptions.value,
      compilerClasspath = (KotlinInternal / managedClasspath).value
    )
  }.tag(Tags.Compile, Tags.CPU)

  implicit class DefOp(singleton: Def.type) {
    def uncached[A](a: A): A = a
  }
}
