package sbt

import sbt.Keys.*
import sbt.internal.inc.*
import sbt.internal.inc.classfile.JavaAnalyze
import sbt.internal.inc.classpath.ClasspathUtil
import xsbti.compile.*
import xsbti.{VirtualFile, VirtualFileRef}

object KotlinTest {
  private object EmptyLookup extends Lookup {
    override def changedClasspathHash: Option[Vector[FileHash]] = None

    override def analyses: Vector[CompileAnalysis] = Vector.empty

    override def lookupOnClasspath(binaryClassName: String): Option[VirtualFileRef] = None

    override def lookupAnalysis(binaryClassName: String): Option[CompileAnalysis] = None

    override def changedBinaries(previousAnalysis: CompileAnalysis): Option[Set[VirtualFileRef]] = None

    override def changedSources(previousAnalysis: CompileAnalysis): Option[xsbti.compile.Changes[VirtualFileRef]] = None

    override def removedProducts(previousAnalysis: CompileAnalysis): Option[Set[VirtualFileRef]] = None

    override def shouldDoIncrementalCompilation(changedClasses: Set[String], analysis: CompileAnalysis): Boolean = true

    override def hashClasspath(classpath: Array[VirtualFile]): java.util.Optional[Array[xsbti.compile.FileHash]] = java.util.Optional.empty()
  }

  val kotlinTests = Def.task {
    val out = ((Test / target).value ** "scala-*").get.head / "test-classes"
    val srcs = ((Test / sourceDirectory).value ** "*.kt").get.toList.map(file => PlainVirtualFile(file.toPath))
    val xs = (out ** "*.class").get.toList

    val loader = ClasspathUtil.toLoader((Test / fullClasspath).value.map(_.data))
    val log = streams.value.log
    val output = new SingleOutput {
      def getOutputDirectory: File = out
    }

    val incremental = Incremental(
      srcs.toSet,
      PlainVirtualFileConverter.converter,
      EmptyLookup,
      Analysis.Empty,
      IncOptions.of(),
      MiniSetup.of(
        output,
        MiniOptions.of(null, (Test / scalacOptions).value.toArray, (Test / javacOptions).value.toArray),
        null,
        null,
        false,
        null
      ),
      (Test / compile).value.readStamps(),
      output,
      JarUtils.createOutputJarContent(output),
      None,
      None,
      None,
      log,
    ) _

    val a0 = incremental { (_, _, callback, _) =>
      def readAPI(source: VirtualFileRef, classes: Seq[Class[?]]): Set[(String, String)] = {
        val (apis, mainClasses, inherits) = ClassToAPI.process(classes)
        apis.foreach(callback.api(source, _))
        mainClasses.foreach(callback.mainClass(source, _))
        inherits.map {
          case (from, to) => (from.getName, to.getName)
        }
      }

      JavaAnalyze(xs.map(_.toPath), srcs, log, output, None)(callback, loader, readAPI)
    }._2
    val frameworks = (Test / loadedTestFrameworks).value.values.toList
    if (srcs.nonEmpty) {
      log.info(s"Compiling ${srcs.length} Kotlin source to $out ...")
    }
    Tests.discover(frameworks, a0, log)._1
  }
}
