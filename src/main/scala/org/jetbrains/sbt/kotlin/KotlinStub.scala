package org.jetbrains.sbt.kotlin

import sbt.util.Logger

import java.lang.reflect.Method
import scala.jdk.CollectionConverters.*

case class KotlinStub(log: Logger, kref: KotlinReflection) {
  import kref.*

  def messageCollector: AnyRef = {
    import java.lang.reflect.{InvocationHandler, Proxy}
    val messageCollectorInvocationHandler = new InvocationHandler {
      override def invoke(proxy: scala.Any, method: Method, args: Array[AnyRef]): AnyRef = {
        method.getName match {
          case "report" =>
            val Array(severity, message, location) = args
            val msg =
              if (location == null) message.toString
              else {
                val locationProxy = new CompilerMessageLocationProxy(location)
                val path = locationProxy.path
                val line = locationProxy.line
                val column = locationProxy.column
                s"$path:$line:$column: $message"
              }
            severity.toString match {
              case "INFO"                 => log.info(msg)
              case "WARNING"              => log.warn(msg)
              case "STRONG_WARNING"       => log.warn(msg)
              case "ERROR"  | "EXCEPTION" => log.error(msg)
              case "OUTPUT" | "LOGGING"   => log.debug(msg)
            }
            null

          case "hasErrors" => java.lang.Boolean.FALSE

          case _ => null
        }
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

  def compilerArgs: CompilerArgs = new CompilerArgs(kref)

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
