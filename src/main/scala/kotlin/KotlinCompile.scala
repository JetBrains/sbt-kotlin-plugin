package kotlin

import sbt.*
import sbt.Keys.{Classpath, TaskStreams}
import sbt.internal.inc.classpath.ClasspathUtil
import sbt.io.*

import java.io.File
import java.lang.reflect.{Field, Method}
import java.util.jar.JarEntry
import scala.collection.JavaConverters.*
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

  private lazy val memoizedKotlinReflection =
    memoize[Classpath, KotlinReflection](KotlinReflection.fromClasspath)

  def grepjar(jarfile: File)(pred: JarEntry => Boolean): Boolean =
    jarfile.isFile && Using.jarFile(false)(jarfile) { in =>
      in.entries.asScala exists pred
    }

  def compile(kotlinVersion: String,
              options: Seq[String],
              jvmTarget: String,
              sourceDirs: Seq[File],
              kotlinPluginOptions: Seq[String],
              classpath: Classpath,
              compilerClasspath: Classpath,
              output: File, s: TaskStreams): Unit = {
    import language.reflectiveCalls
    val stub = KotlinStub(s, memoizedKotlinReflection(compilerClasspath))
    val args = stub.compilerArgs
    stub.parse(kotlinVersion, args.instance, options.toList)
    val kotlinFiles = "*.kt" || "*.kts"
    val javaFiles = "*.java"

    val kotlinSources = sourceDirs.flatMap(d => (d ** kotlinFiles).get).distinct
    val javaSources = sourceDirs.filterNot(f => sourceDirs.exists(f0 =>
      f0.relativeTo(f).isDefined && f != f0)) map (d =>
      (d, (d ** javaFiles).get)) filter (_._2.nonEmpty)
    if (kotlinSources.isEmpty) {
      s.log.debug("No sources found, skipping Kotlin compile")
    } else {
      s.log.debug(s"Compiling sources $kotlinSources")
      def pluralizeSource(count: Int) =
        if (count == 1) "source" else "sources"
      val message =
        s"Compiling ${kotlinSources.size} Kotlin ${pluralizeSource(kotlinSources.size)} to ${output.getAbsolutePath} ..."
      s.log.info(message)
      args.freeArgs = (kotlinSources ++ javaSources.map(_._1)).map(_.getAbsolutePath).asJava
      args.noStdlib = true
      args.jvmTarget = jvmTarget
      val fcpjars = classpath.map(_.data.getAbsoluteFile)
      val (pluginjars, cpjars) = fcpjars.partition {
        grepjar(_)(_.getName.startsWith(
          "META-INF/services/org.jetbrains.kotlin.compiler.plugin"))
      }
      val cp = cpjars.mkString(File.pathSeparator)
      val pcp = pluginjars.map(_.getAbsolutePath).toArray
      args.classpath = Option(args.classpath[String]).fold(cp)(_ + File.pathSeparator + cp)
      args.pluginClasspaths = Option(args.pluginClasspaths[Array[String]]).fold(pcp)(_ ++ pcp)
      args.pluginOptions = Option(args.pluginOptions[Array[String]]).fold(
        kotlinPluginOptions.toArray)(_ ++ kotlinPluginOptions.toArray[String])
      output.mkdirs()
      args.destination = output.getAbsolutePath
      stub.compile(args.instance)
    }
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

  def compile(args: AnyRef): Unit = {
    val compiler = compilerClass.getDeclaredConstructor().newInstance()
    val result = compilerExec.invoke(compiler,
      messageCollector, servicesEmptyField.get(null), args: java.lang.Object)
    result.toString match {
      case "COMPILATION_ERROR" | "INTERNAL_ERROR" =>
        throw new MessageOnlyException("Compilation failed. See log for more details")
      case _ =>
    }
  }
}
