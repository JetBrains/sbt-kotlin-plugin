package org.jetbrains.sbt.kotlin

import sbt.Keys.TaskStreams

import java.lang.reflect.Method
import scala.jdk.CollectionConverters.seqAsJavaListConverter

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
      override def invoke(proxy: scala.Any, method: Method, args: Array[AnyRef]): AnyRef = {
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
          return null
        } else if (method.getName == "hasErrors") {
          return java.lang.Boolean.FALSE
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