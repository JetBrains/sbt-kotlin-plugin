package org.jetbrains.sbt.kotlin

import sbt.Keys.*
import sbt.internal.inc.*
import sbt.internal.inc.caching.ClasspathCache
import sbtcompat.PluginCompat.toNioPaths
import xsbti.FileConverter
import xsbti.compile.*

import java.nio.file.{Files, Path}

private[kotlin] object KotlinCompile {

  private def memoize[K, V](f: K => V): K => V = {
    val cache = new java.util.concurrent.ConcurrentHashMap[K, V]()
    // don't inline
    cache.computeIfAbsent(_, f(_))
  }

  private[kotlin] lazy val memoizedKotlinReflection =
    memoize[Seq[Path], KotlinReflection](KotlinReflection.fromClasspath)

  private[kotlin] def compileIncremental(
    streams: TaskStreams,
    inputs: Inputs,
    converter: FileConverter,
    kotlinVersion: String,
    kotlincOptions: Seq[String],
    kotlincJvmTarget: String,
    kotlinModuleName: String,
    kotlincPluginOptions: Seq[String],
    classpathOptions: ClasspathOptions,
    compilerClasspath: Classpath
  ): CompileResult = {
    val out = inputs.options().classesDirectory().toAbsolutePath.normalize()

    val srcs = inputs.options().sources().toSet

    val output: SingleOutput = new SingleOutput {
      override def getOutputDirectory: java.io.File = out.toFile
      override def getOutputDirectoryAsPath: Path = out
    }

    val previousResult = inputs.previousResult()
    val previousAnalysis = previousResult.analysis().orElse(Analysis.empty)

    val classpath = inputs.options().classpath()
    val classpathIndexedSeq = classpath.toIndexedSeq
    val classpathAsNioPaths = classpathIndexedSeq.map(converter.toPath(_).toAbsolutePath.normalize())

    val stamper = inputs.options().stamper().orElseGet(() => Stamps.timeWrapBinaryStamps(converter))

    val javacOptionsAsIndexedSeq = inputs.options().javacOptions().toIndexedSeq

    val config = {
      val outputJarContent = JarUtils.createOutputJarContent(output)

      MixedAnalyzingCompiler.makeConfig(
        inputs.compilers().scalac(),
        inputs.compilers().javaTools().javac(),
        srcs.toSeq,
        converter,
        classpathIndexedSeq,
        inputs.setup().cache(),
        optionalToOption(inputs.setup().progress()),
        inputs.options().scalacOptions().toIndexedSeq,
        javacOptionsAsIndexedSeq,
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
        ClasspathCache.hashClasspath(classpathAsNioPaths)
    }

    val miniSetup = MiniSetup.of(
      output,
      MiniOptions.of(classpathHash, inputs.options().scalacOptions(), inputs.options().javacOptions()),
      kotlinVersion,
      inputs.options().order(),
      true,
      inputs.setup().extra()
    )

    val (searchClasspath, _) = MixedAnalyzingCompiler.searchClasspathAndLookup(config)

    if (!Files.exists(out)) {
      Files.createDirectories(out)
    }

    val compilerClasspathAsNioPaths = {
      implicit val c: FileConverter = converter
      toNioPaths(compilerClasspath)
    }

    val compiler = new AnalyzingKotlinCompiler(
      kotlinVersion,
      kotlincOptions,
      kotlincJvmTarget,
      kotlinModuleName,
      kotlincPluginOptions,
      inputs.compilers().javaTools().javac(),
      javacOptionsAsIndexedSeq,
      inputs.compilers().scalac().scalaInstance(),
      inputs.setup().incrementalCompilerOptions().useCustomizedFileManager(),
      config.sources,
      classpathOptions,
      classpathAsNioPaths,
      compilerClasspathAsNioPaths,
      searchClasspath,
      output,
      converter,
      inputs.setup().reporter(),
      config.progress,
      streams.log
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
      streams.log
    )(compiler.compile)

    CompileResult.of(analysis, miniSetup, success)
  }

  private def optionalToOption[A](optional: java.util.Optional[A]): Option[A] =
    if (optional.isPresent) Some(optional.get) else None
}
