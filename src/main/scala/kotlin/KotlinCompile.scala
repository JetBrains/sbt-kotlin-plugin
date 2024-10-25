package kotlin

import kotlin.Keys.*
import sbt.Keys.*
import sbt.internal.inc.*
import sbt.internal.inc.caching.ClasspathCache
import sbt.internal.inc.classpath.ClasspathUtil
import sbt.*
import xsbti.compile.*

import java.io.File
import java.lang.reflect.{Field, Method}
import scala.collection.JavaConverters.*
import scala.jdk.OptionConverters.*
import scala.util.Try

/**
 * @author pfnguyen
 */
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
        inputs.setup().progress().toScala,
        inputs.options().scalacOptions(),
        inputs.options().javacOptions(),
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
}

object KotlinReflection {
  def fromClasspath(cp: Classpath): KotlinReflection = {
    val cl = ClasspathUtil.toLoader(cp.map(_.data))
    val compilerClass = cl.loadClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    val servicesClass = cl.loadClass("org.jetbrains.kotlin.config.Services")
    val messageCollectorClass = cl.loadClass("org.jetbrains.kotlin.cli.common.messages.MessageCollector")
    val commonCompilerArgsClass = cl.loadClass("org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments")

    val compilerExec = Try(
      compilerClass.getMethod("exec",
        messageCollectorClass, servicesClass, commonCompilerArgsClass)
      ).toOption.getOrElse {

        val commonToolArguments = cl.loadClass(
          "org.jetbrains.kotlin.cli.common.arguments.CommonToolArguments")
        val clitool = cl.loadClass(
          "org.jetbrains.kotlin.cli.common.CLITool")
        clitool.getMethod("exec",
          messageCollectorClass, servicesClass, commonToolArguments)
      }

    KotlinReflection(
      cl,
      servicesClass,
      compilerClass,
      cl.loadClass("org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments"),
      messageCollectorClass,
      commonCompilerArgsClass,
      compilerExec,
      servicesClass.getDeclaredField("EMPTY"))
  }
}

case class KotlinReflection(cl: ClassLoader,
                            servicesClass: Class[?],
                            compilerClass: Class[?],
                            compilerArgsClass: Class[?],
                            messageCollectorClass: Class[?],
                            commonCompilerArgsClass: Class[?],
                            compilerExec: Method,
                            servicesEmptyField: Field)

case class KotlinStub(s: TaskStreams, kref: KotlinReflection) {
  import kref.*

  import language.reflectiveCalls

  def messageCollector: AnyRef = {
    type CompilerMessageLocation = {
      def getPath: String
      def getLine: Int
      def getColumn: Int
    }

    import java.lang.reflect.{InvocationHandler, Proxy}
    val messageCollectorInvocationHandler = new InvocationHandler {
      override def invoke(proxy: scala.Any, method: Method, args: Array[AnyRef]) = {
        if (method.getName == "report") {
          val Array(severity, message, location) = args
          val l = location.asInstanceOf[CompilerMessageLocation]
          val msg = Option(l).map(x => x.getPath).fold(message.toString)(loc =>
            loc + ": " + l.getLine + ", " + l.getColumn + ": " + message)
          severity.toString match {
            case "INFO"                 => s.log.info(msg)
            case "WARNING"              => s.log.warn(msg)
            case "STRONG_WARNING"       => s.log.warn(msg)
            case "ERROR"  | "EXCEPTION" => s.log.error(msg)
            case "OUTPUT" | "LOGGING"   => s.log.debug(msg)
          }
        }
        null
      }
    }

    Proxy.newProxyInstance(cl, Array(messageCollectorClass), messageCollectorInvocationHandler)
  }

  def parse(kotlinVersion: String, args: Object, options: List[String]): Unit = {
    // TODO FIXME, this is much worse than it used to be, the parsing api has been
    // deeply in flux since 1.1.x
    val parser = kref.cl.loadClass(
      "org.jetbrains.kotlin.cli.common.arguments.ParseCommandLineArgumentsKt")
    val commonToolArguments = cl.loadClass(
      "org.jetbrains.kotlin.cli.common.arguments.CommonToolArguments")

    val parseMethodName = "parseCommandLineArguments"
    val stringListClass = classOf[java.util.List[java.lang.String]]

    // parse method received a new argument in 1.7.0
    // see https://github.com/JetBrains/kotlin/commit/683a3e74a000f959a932505592e1d68a073296cd
    if (KotlinVersion(kotlinVersion) >= KotlinVersion("1.7.0")) {
      val parserMethod = parser.getMethod(
        parseMethodName,
        stringListClass,
        commonToolArguments,
        classOf[Boolean]
      )
      // false is a default value
      parserMethod.invoke(null, options.asJava, args, java.lang.Boolean.FALSE)
    } else {
      val parserMethod = parser.getMethod(
        parseMethodName,
        stringListClass,
        commonToolArguments
      )
      parserMethod.invoke(null, options.asJava, args)
    }
  }

  def compilerArgs = {
    import language.dynamics
    new Dynamic {
      def withFirstUpper(string: String): String = string.head.toUpper + string.tail
      def getterName(field: String) = s"get${withFirstUpper(field)}"
      def setterName(field: String) = s"set${withFirstUpper(field)}"

      def selectDynamic[A](field: String): A = {
        val methodName = getterName(field)
        val getterOpt = compilerArgsClass.getMethods.find(_.getName == methodName)
        getterOpt match {
          case Some(getter) => getter.invoke(instance).asInstanceOf[A]
          case None => compilerArgsClass.getField(field).get(instance).asInstanceOf[A]
        }
      }

      def updateDynamic(field: String)(value: Any): Unit = {
        val methodName = setterName(field)
        val setterOpt = compilerArgsClass.getMethods.find(_.getName == methodName)
        setterOpt match {
          case Some(setter) => setter.invoke(instance, value.asInstanceOf[Object])
          case None => compilerArgsClass.getField(field).set(instance, value)
        }
      }

      val instance = compilerArgsClass.getDeclaredConstructor().newInstance().asInstanceOf[AnyRef]
    }
  }

  def compile(args: AnyRef): Boolean = {
    val compiler = compilerClass.getDeclaredConstructor().newInstance()
    val result = compilerExec.invoke(compiler,
      messageCollector, servicesEmptyField.get(null), args: java.lang.Object)
    result.toString match {
      case "COMPILATION_ERROR" | "INTERNAL_ERROR" => false
      case _ => true
    }
  }
}
