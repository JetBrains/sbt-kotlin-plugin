package kotlin

import sbt.JavaAnalyzeBridge
import sbt.Keys.{Classpath, TaskStreams}
import sbt.internal.inc.classpath.ClasspathUtil
import sbt.internal.inc.javac.{DirectoryClassFinder, JarClassFinder}
import sbt.internal.inc.{ClassToAPI, CompileFailed}
import sbt.io.Using
import sbt.util.Logger
import xsbti.*
import xsbti.compile.*

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.Optional
import java.util.jar.JarEntry
import scala.annotation.unused
import scala.jdk.CollectionConverters.*

/*
 * The source code of this file has been developed with heavy inspiration from
 * the source code of `sbt.internal.inc.javac.AnalyzingJavaCompiler`, as seen in
 * https://github.com/sbt/zinc/blob/7aa92cb48872f28dd44c1bb7520a84a0e02f230e/zinc/src/main/scala/sbt/internal/inc/javac/AnalyzingJavaCompiler.scala.
 */
class AnalyzingKotlinCompiler(
  kotlinVersion: String,
  kotlinOptions: Seq[String],
  jvmTarget: String,
  kotlinPluginOptions: Seq[String],
  javac: JavaCompiler,
  javacOptions: Seq[String],
  scalaInstance: ScalaInstance,
  useCustomizedFileManager: Boolean,
  allSources: Seq[VirtualFile],
  classpathOptions: ClasspathOptions,
  classpath: Classpath,
  compilerClasspath: Classpath,
  searchClasspath: Seq[VirtualFile],
  out: File,
  converter: FileConverter,
  reporter: Reporter,
  progressOpt: Option[CompileProgress],
  s: TaskStreams
) {

  def compile(
    @unused incrementalSources: Set[VirtualFile],
    @unused changes: DependencyChanges,
    callback: AnalysisCallback,
    classFileManager: ClassFileManager
  ): Unit = {
    val allSourceFiles = allSources.map(vf => converter.toPath(vf).toFile)

    val kotlinSources = allSourceFiles.filter { f =>
      val name = f.getName
      name.endsWith(".kt") || name.endsWith(".kts")
    }
    val javaSources = allSourceFiles.filter(_.getName.endsWith(".java"))

    if (kotlinSources.nonEmpty || javaSources.nonEmpty) {
      // Outline chunks of compiles so that .class files end up in right location
      val chunks: Map[Option[Path], Seq[VirtualFile]] = Map(Option(out.toPath) -> allSources)

      // Memoize the known class files in the Javac output location
      val memo = for { case (Some(outputPath), srcs) <- chunks } yield {
        val classFinder =
          if (outputPath.toString.endsWith(".jar")) new JarClassFinder(outputPath)
          else new DirectoryClassFinder(outputPath)
        (classFinder, classFinder.classes.pathsAndClose(), srcs)
      }

      def pluralizeSource(count: Int) =
        if (count == 1) "source" else "sources"

      val message =
        if (kotlinSources.nonEmpty) {
          val ktCount = kotlinSources.size
          if (javaSources.nonEmpty) {
            val javaCount = javaSources.size
            s"compiling $ktCount Kotlin ${pluralizeSource(ktCount)} and $javaCount Java ${pluralizeSource(javaCount)} to ${out.getAbsolutePath} ..."
          } else {
            s"compiling $ktCount Kotlin ${pluralizeSource(ktCount)} to ${out.getAbsolutePath} ..."
          }
        } else {
          val javaCount = javaSources.size
          s"compiling $javaCount Java ${pluralizeSource(javaCount)} to ${out.getAbsolutePath} ..."
        }
      s.log.info(message)

      // Record progress for Kotlin and Java compilation
      val somePhase = "<some phase>"
      val noPhase = "<no phase>"
      val kotlinCompilationPhase = "Kotlin compilation"
      val javaCompilationPhase = "Java compilation"
      val bytecodeAnalysisPhase = "Bytecode analysis"

      progressOpt.foreach { progress =>
        progress.startUnit(kotlinCompilationPhase, "")
        progress.advance(0, 3, somePhase, kotlinCompilationPhase)
      }

      if (kotlinSources.nonEmpty) {
        s.log.debug(s"compiling Kotlin sources: $kotlinSources")

        timed(kotlinCompilationPhase, s.log) {
          import language.reflectiveCalls
          val stub = KotlinStub(s, KotlinCompile.memoizedKotlinReflection(compilerClasspath))
          val args = stub.compilerArgs
          stub.parse(kotlinVersion, args.instance, "-Xallow-no-source-files" :: kotlinOptions.toList)
          args.multiPlatform = false
          args.noStdlib = true
          args.noReflect = true
          args.jvmTarget = jvmTarget
          args.friendPaths = Array(out.getAbsolutePath)
          args.freeArgs = (kotlinSources ++ javaSources).map(_.getAbsolutePath).asJava
          val fcpjars = classpath.map(_.data.getAbsoluteFile)
          val (pluginjars, cpjars) = fcpjars.partition {
            grepjar(_)(_.getName.startsWith(
              "META-INF/services/org.jetbrains.kotlin.compiler.plugin"))
          }
          val cp = out.getAbsolutePath + File.pathSeparator + cpjars.mkString(File.pathSeparator)
          val pcp = pluginjars.map(_.getAbsolutePath).toArray
          args.classpath = Option(args.classpath[String]).fold(cp)(_ + File.pathSeparator + cp)
          args.pluginClasspaths = Option(args.pluginClasspaths[Array[String]]).fold(pcp)(_ ++ pcp)
          args.pluginOptions = Option(args.pluginOptions[Array[String]]).fold(
            kotlinPluginOptions.toArray)(_ ++ kotlinPluginOptions.toArray[String])
          args.destination = out.getAbsolutePath
          val success = stub.compile(args.instance)

          if (!success) {
            val msg = "kotlinc returned non-zero exit code"
            throw new CompileFailed(Array(args.toString), msg, reporter.problems())
          }
        }
      }

      val output = new SingleOutput {
        override def getOutputDirectory: File = out
      }

      progressOpt.foreach { progress =>
        progress.startUnit(javaCompilationPhase, "")
        progress.advance(1, 3, kotlinCompilationPhase, javaCompilationPhase)
      }

      if (javaSources.nonEmpty) {
        s.log.debug(s"compiling Java sources: $javaSources")

        timed(javaCompilationPhase, s.log) {
          val absoluteClasspath = converter.toVirtualFile(out.toPath) +: classpath.map(_.data.toPath).map(converter.toVirtualFile)
          val args = sbt.internal.inc.javac.JavaCompiler.commandArguments(
            absoluteClasspath,
            converter,
            javacOptions,
            scalaInstance,
            classpathOptions
          )
          val javaSrcs = javaSources.map(f => converter.toVirtualFile(f.toPath)).toArray
          val incToolOptions = IncToolOptions.of(
            Optional.of(classFileManager),
            useCustomizedFileManager
          )

          val success =
            javac.run(javaSrcs, args.toArray, output, incToolOptions, reporter, s.log)

          if (!success) {
            /* Assume that no Scalac problems are reported for a Javac-related
             * reporter. This relies on the incremental compiler will not run
             * Javac compilation if Scala compilation fails, which means that
             * the same reporter won't be used for `AnalyzingJavaCompiler`. */
            val msg = "javac returned non-zero exit code"
            throw new CompileFailed(args.toArray, msg, reporter.problems())
          }
        }
      }

      // Analysis
      // Read the API information from [[Class]] to analyze dependencies.
      def readAPI(source: VirtualFileRef, classes: Seq[Class[?]]): Set[(String, String)] = {
        val (apis, mainClasses, inherits) = ClassToAPI.process(classes)
        apis.foreach(callback.api(source, _))
        mainClasses.foreach(callback.mainClass(source, _))
        inherits.map {
          case (from, to) => (from.getName, to.getName)
        }
      }

      progressOpt.foreach { progress =>
        progress.startUnit(bytecodeAnalysisPhase, "")
        progress.advance(2, 3, javaCompilationPhase, bytecodeAnalysisPhase)
      }

      // Construct class loader to analyze dependencies of generated class files
      val loader = ClasspathUtil.toLoader(
        Seq(out.toPath) ++ classpath.files.map(_.toPath) ++ searchClasspath.map(converter.toPath)
      )

      timed(bytecodeAnalysisPhase, s.log) {
        for {
          (classFinder, oldClasses, srcs) <- memo
        } {
          val classes = classFinder.classes
          try {
            val newClasses = classes.paths.toSet -- oldClasses
            classFileManager.generated(newClasses.toArray.map(converter.toVirtualFile))
            JavaAnalyzeBridge(newClasses.toSeq, srcs, s.log, output, None)(callback, loader, readAPI)
          } finally classes.close()
        }
      }

      // After using the classloader it should be closed. Otherwise it will keep the accessed
      // jars open. Especially, when zinc is compiling directly to jar, that jar will be locked
      // not allowing to change it in further compilation cycles (on Windows).
      // This also affects jars in the classpath that come from dependency resolution.
      loader match {
        case u: URLClassLoader => u.close()
        case _                 => ()
      }

      // Report that we reached the end
      progressOpt.foreach { progress =>
        progress.advance(3, 3, bytecodeAnalysisPhase, noPhase)
      }
    }
  }

  private def grepjar(jarfile: File)(pred: JarEntry => Boolean): Boolean =
    jarfile.isFile && Using.jarFile(false)(jarfile) { in =>
      in.entries.asScala exists pred
    }

  /** Time how long it takes to run various compilation tasks. */
  private[this] def timed[T](label: String, log: Logger)(t: => T): T = {
    val start = System.nanoTime
    val result = t
    val elapsed = System.nanoTime - start
    log.debug(label + " took " + (elapsed / 1e9) + " s")
    result
  }
}
