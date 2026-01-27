package org.jetbrains.sbt.kotlin

import org.jetbrains.sbt.kotlin.Keys.*
import sbt.*
import sbt.Keys.*
import sbt.internal.inc.*
import sbt.internal.inc.caching.ClasspathCache
import xsbti.compile.*

import java.io.File

object KotlinCompile {

  private def memoize[K, V](f: K => V): K => V = {
    val cache = new java.util.concurrent.ConcurrentHashMap[K, V]()
    // don't inline
    cache.computeIfAbsent(_, f(_))
  }

  private[kotlin] lazy val memoizedKotlinReflection =
    memoize[Classpath, KotlinReflection](KotlinReflection.fromClasspath)

  def compileTask: Def.Initialize[Task[CompileResult]] = Def.task {
    val logStreams = streams.value
    val inputs = (compile / compileInputs).value
    val converter = inputs.options().converter().orElse(PlainVirtualFileConverter.converter)
    val out = inputs.options().classesDirectory()

    val srcs = inputs.options().sources().toSet

    val output = new SingleOutput {
      override def getOutputDirectory: File = out.toFile
    }

    val kotlincVersion = kotlinVersion.value

    val previousResult = inputs.previousResult()
    val previousAnalysis = previousResult.analysis().orElse(Analysis.empty)

    val classpath = inputs.options().classpath()

    val stamper = inputs.options().stamper().orElseGet(() => Stamps.timeWrapBinaryStamps(converter))

    val config = {
      val outputJarContent = JarUtils.createOutputJarContent(output)

      MixedAnalyzingCompiler.makeConfig(
        inputs.compilers().scalac(),
        inputs.compilers().javaTools().javac(),
        srcs.toSeq,
        converter,
        classpath.toSeq,
        inputs.setup().cache(),
        optionalToOption(inputs.setup().progress()),
        inputs.options().scalacOptions(),
        inputs.options().javacOptions(),
        previousAnalysis,
        optionalToOption(previousResult.setup()),
        inputs.setup().perClasspathEntryLookup(),
        inputs.setup().reporter(),
        inputs.options().order(),
        inputs.setup().skip(),
        inputs.setup().incrementalCompilerOptions(),
        output,
        outputJarContent,
        None,
        None,
        stamper,
        inputs.setup().extra().toList.map(t => (t.get1(), t.get2()))
      )
    }

    val lookup = new LookupImpl(config, optionalToOption(previousResult.setup()))

    val classpathHash = {
      val fromLookup = lookup.hashClasspath(classpath)
      if (fromLookup.isPresent)
        fromLookup.get()
      else
        ClasspathCache.hashClasspath(classpath.map(converter.toPath))
    }

    val miniSetup = MiniSetup.of(
      output,
      MiniOptions.of(classpathHash, inputs.options().scalacOptions(), inputs.options().javacOptions()),
      kotlincVersion,
      inputs.options().order(),
      true,
      inputs.setup().extra()
    )

    val (searchClasspath, _) = MixedAnalyzingCompiler.searchClasspathAndLookup(config)

    val outDir = out.toFile
    if (!outDir.exists()) {
      outDir.mkdirs()
    }

    val compiler = new AnalyzingKotlinCompiler(
      kotlincVersion,
      kotlincOptions.value,
      kotlincJvmTarget.value,
      kotlinModuleName.value,
      kotlincPluginOptions.value,
      inputs.compilers().javaTools().javac(),
      inputs.options().javacOptions(),
      inputs.compilers().scalac().scalaInstance(),
      inputs.setup().incrementalCompilerOptions().useCustomizedFileManager(),
      config.sources,
      classpathOptions.value,
      dependencyClasspath.value,
      (KotlinInternal / managedClasspath).value,
      searchClasspath,
      outDir,
      converter,
      inputs.setup().reporter(),
      config.progress,
      logStreams
    )

    val (success, analysis) = Incremental(
      srcs,
      converter,
      lookup,
      previousAnalysis,
      inputs.setup().incrementalCompilerOptions(),
      miniSetup,
      stamper,
      output,
      JarUtils.createOutputJarContent(output),
      None,
      None,
      config.progress,
      logStreams.log,
    )(compiler.compile)

    CompileResult.of(analysis, miniSetup, success)
  }

  private def optionalToOption[A](optional: java.util.Optional[A]): Option[A] =
    if (optional.isPresent) Some(optional.get) else None
}
