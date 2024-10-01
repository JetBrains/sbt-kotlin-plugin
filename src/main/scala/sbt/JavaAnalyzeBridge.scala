package sbt

import sbt.internal.inc.classfile.JavaAnalyze
import xsbti.compile.Output
import xsbti.{VirtualFile, VirtualFileRef}

object JavaAnalyzeBridge {
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
  ): Unit = JavaAnalyze(newClasses, sources, log, output, finalJarOutput)(analysis, loader, readAPI)
}
