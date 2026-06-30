package org.jetbrains.sbt.kotlin

import org.jetbrains.sbt.kotlin.Keys
import sbt.*
import sbt.Keys.*
import sbt.internal.inc.MixedAnalyzingCompiler
import sbt.util.CacheImplicits.given
import xsbti.{FileConverter, HashedVirtualFileRef, VirtualFileRef}
import xsbti.compile.AnalysisContents

private[kotlin] object CompileIncrementalCompat:
  def compileIncrementalTaskImpl: Def.Initialize[Task[(Boolean, VirtualFileRef, HashedVirtualFileRef)]] = Def.cachedTask {
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
