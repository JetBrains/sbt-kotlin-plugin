package sbt

import sbt.internal.inc.classfile.ClassFile
import xsbti.compile.Output
import xsbti.{AnalysisCallback, VirtualFile, VirtualFileRef}

object JavaAnalyzeBridge:
  def apply(
    newClasses: Seq[java.nio.file.Path],
    sources: Seq[VirtualFile],
    log: Logger,
    output: Output,
    finalJarOutput: Option[java.nio.file.Path]
  )(
    analysis: xsbti.AnalysisCallback,
    loader: ClassLoader,
    readAPI: (VirtualFileRef, Seq[Class[?]]) => Set[(String, String)]
  ): Unit =
    val cls = Class.forName("sbt.internal.inc.classfile.JavaAnalyze")
    val method = cls.getDeclaredMethods.find(_.getName == "apply").getOrElse(sys.error("Could not find JavaAnalyze.apply method"))
    method.getParameterCount match
      case 8 =>
        // sbt 2 versions up to and including 2.0.0 have an apply method with 8 paremeters
        method.invoke(null, newClasses, sources, log, output, finalJarOutput, analysis, loader, readAPI)
      case 10 =>
        // sbt 2.0.1 and up have an apply method with 10 parameters
        val readClassfileAPI: (VirtualFileRef, Seq[(String, ClassFile)]) => Unit = (_, _) => ()
        val constantDeps = Map.empty[String, Set[String]]
        method.invoke(null, newClasses, sources, log, output, finalJarOutput, analysis, loader, readAPI, readClassfileAPI, constantDeps)
      case n =>
        sys.error(s"Unrecognized JavaAnalyze.apply method with $n parameters: $method")
