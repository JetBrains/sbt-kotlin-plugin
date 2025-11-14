package org.jetbrains.sbt.kotlin

import scala.language.dynamics

class CompilerArgs(reflection: KotlinReflection) extends Dynamic {
  private def withFirstUpper(string: String): String = string.head.toUpper.toString ++ string.tail
  private def getterName(field: String) = s"get${withFirstUpper(field)}"
  private def setterName(field: String) = s"set${withFirstUpper(field)}"

  def selectDynamic[A](field: String): A = {
    val methodName = getterName(field)
    val getterOpt = reflection.compilerArgsClass.getMethods.find(_.getName == methodName)
    getterOpt match {
      case Some(getter) => getter.invoke(instance).asInstanceOf[A]
      case None => reflection.compilerArgsClass.getField(field).get(instance).asInstanceOf[A]
    }
  }

  def updateDynamic(field: String)(value: Any): Unit = {
    val methodName = setterName(field)
    val setterOpt = reflection.compilerArgsClass.getMethods.find(_.getName == methodName)
    setterOpt match {
      case Some(setter) => setter.invoke(instance, value.asInstanceOf[Object])
      case None => reflection.compilerArgsClass.getField(field).set(instance, value)
    }
  }

  val instance: AnyRef = reflection.compilerArgsClass.getDeclaredConstructor().newInstance().asInstanceOf[AnyRef]
}
