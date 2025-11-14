package org.jetbrains.sbt.kotlin

import sbt.internal.inc.classpath.ClasspathUtil

import java.lang.reflect.{Field, Method}
import java.nio.file.Path
import scala.util.Try

case class KotlinReflection(cl: ClassLoader,
                            servicesClass: Class[?],
                            compilerClass: Class[?],
                            compilerArgsClass: Class[?],
                            messageCollectorClass: Class[?],
                            commonCompilerArgsClass: Class[?],
                            compilerExec: Method,
                            servicesEmptyField: Field)

object KotlinReflection {
  def fromClasspath(cp: Seq[Path]): KotlinReflection = {
    val cl = ClasspathUtil.toLoader(cp)
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
