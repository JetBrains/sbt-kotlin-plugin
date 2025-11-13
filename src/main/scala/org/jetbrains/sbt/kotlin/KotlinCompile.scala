package org.jetbrains.sbt.kotlin

import sbt.*
import sbt.Keys.*
import sbt.internal.inc.*
import sbt.internal.inc.caching.ClasspathCache
import xsbti.FileConverter
import xsbti.compile.*

import java.nio.file.{Files, Path}
import scala.jdk.OptionConverters.*

object KotlinCompile {

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
    val out = inputs.options().classesDirectory()
    val srcs = inputs.options().sources().toSet

    val output = new SingleOutput {
      override def getOutputDirectory: File = out.toFile
      override def getOutputDirectoryAsPath: Path = out
    }

    val previousResult = inputs.previousResult()
    val previousAnalysis = previousResult.analysis().orElse(Analysis.empty)

    val classpath = inputs.options().classpath()
    val classpathAsNioPaths = classpath.toIndexedSeq.map(converter.toPath)

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
        inputs.setup().progress().toScala,
        inputs.options().scalacOptions().toIndexedSeq,
        inputs.options().javacOptions().toIndexedSeq,
        previousAnalysis,
        previousResult.setup().toScala,
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

    val lookup = new LookupImpl(config, previousResult.setup().toScala)

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

    val compiler = new AnalyzingKotlinCompiler(
      kotlinVersion,
      kotlincOptions,
      kotlincJvmTarget,
      kotlinModuleName,
      kotlincPluginOptions,
      inputs.compilers().javaTools().javac(),
      inputs.options().javacOptions().toIndexedSeq,
      inputs.compilers().scalac().scalaInstance(),
      inputs.setup().incrementalCompilerOptions().useCustomizedFileManager(),
      config.sources,
      classpathOptions,
      classpathAsNioPaths,
      compilerClasspath,
      searchClasspath,
      out,
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
      streams.log,
    )(compiler.compile)

    CompileResult.of(analysis, miniSetup, success)
  }
}
