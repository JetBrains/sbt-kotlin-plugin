package org.jetbrains.sbt.kotlin

import scala.language.dynamics

/**
 * Encapsulates reflective access to the Kotlin compiler internal args class.
 */
class CompilerArgs(kref: KotlinReflection) extends Dynamic {
  val instance: AnyRef = kref.compilerArgsClass.getDeclaredConstructor().newInstance().asInstanceOf[AnyRef]

  def selectDynamic[A](field: String): A = {
    val methodName = getterName(field)
    val getterOpt = kref.compilerArgsClass.getMethods.find(_.getName == methodName)
    getterOpt match {
      case Some(getter) => getter.invoke(instance).asInstanceOf[A]
      case None => kref.compilerArgsClass.getField(field).get(instance).asInstanceOf[A]
    }
  }

  def updateDynamic(field: String)(value: Any): Unit = {
    val methodName = setterName(field)
    val setterOpt = kref.compilerArgsClass.getMethods.find(_.getName == methodName)
    setterOpt match {
      case Some(setter) => setter.invoke(instance, value.asInstanceOf[Object])
      case None => kref.compilerArgsClass.getField(field).set(instance, value)
    }
  }

  private def getterName(field: String) = s"get${withFirstUpper(field)}"
  private def setterName(field: String) = s"set${withFirstUpper(field)}"
  private def withFirstUpper(string: String): String = string.head.toUpper + string.tail
}
