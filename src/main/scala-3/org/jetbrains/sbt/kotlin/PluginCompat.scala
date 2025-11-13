package org.jetbrains.sbt.kotlin

import org.jetbrains.sbt.kotlin.Keys
import sbt.*
import sbt.Keys.*
import sbt.internal.inc.MixedAnalyzingCompiler
import sbt.util.CacheImplicits.given
import xsbti.{FileConverter, HashedVirtualFileRef, VirtualFileRef}
import xsbti.compile.AnalysisContents

import java.nio.file.Path as NioPath
import scala.language.higherKinds

private[kotlin] object PluginCompat:
  type FileRef = HashedVirtualFileRef

  def toNioPath(fileRef: FileRef, converter: FileConverter): NioPath = converter.toPath(fileRef)

  private def toNioPath(a: Attributed[HashedVirtualFileRef], converter: FileConverter): NioPath =
    converter.toPath(a.data)

  def toNioPaths(cp: Seq[Attributed[HashedVirtualFileRef]], converter: FileConverter): Seq[NioPath] =
    cp.map(toNioPath(_, converter))

  def managedJarsCompat(config: Configuration, jarTypes: Set[String], up: UpdateReport, converter: FileConverter): Classpath =
    Classpaths.managedJars(config, jarTypes, up, converter)

  implicit def seqDerivedOrdering[CC[X] <: scala.collection.Seq[X], T](implicit ord: Ordering[T]): Ordering[CC[T]] =
    scala.math.Ordering.Implicits.seqOrdering

  val compileIncrementalTaskImpl: Def.Initialize[Task[(Boolean, VirtualFileRef, HashedVirtualFileRef)]] = Def.cachedTask {
    val inputs = (compile / compileInputs).value
    // Magic task which needs to be called such that caching of tasks works correctly.
    // Without this line, nothing ever gets recompiled, even when there are changes to source files.
    val inputs2 = (compile / compileInputs2).value
    val converter = fileConverter.value
    val kotlinVersion = Keys.kotlinVersion.value
    val kotlincOptions = Keys.kotlincOptions.value
    val kotlincJvmTarget = Keys.kotlincJvmTarget.value
    val kotlinModuleName = Keys.kotlinModuleName.value
    val kotlincPluginOptions = Keys.kotlincPluginOptions.value
    val analysisFile = compileAnalysisFile.value
    val store = MixedAnalyzingCompiler.staticCachedStore(
      analysisFile = analysisFile.toPath,
      useTextAnalysis = false
    )
    val analysisResult = KotlinCompile.compileIncremental(
      streams = sbt.Keys.streams.value,
      inputs = inputs,
      converter = converter,
      kotlinVersion = kotlinVersion,
      kotlincOptions = kotlincOptions,
      kotlincJvmTarget = kotlincJvmTarget,
      kotlinModuleName = kotlinModuleName,
      kotlincPluginOptions = kotlincPluginOptions,
      classpathOptions = inputs.compilers().scalac().classpathOptions(),
      compilerClasspath = (Keys.KotlinInternal / managedClasspath).value
    )
    val analysisOut = converter.toVirtualFile(inputs.setup().cachePath())
    val contents = AnalysisContents.create(analysisResult.analysis(), analysisResult.setup())
    store.set(contents)
    Def.declareOutput(analysisOut)
    val dir = inputs.options().classesDirectory()
    val vfDir = converter.toVirtualFile(dir)
    val packedDir = Def.declareOutputDirectory(vfDir)
    (analysisResult.hasModified(), vfDir, packedDir)
  }.tag(Tags.Compile, Tags.CPU)
