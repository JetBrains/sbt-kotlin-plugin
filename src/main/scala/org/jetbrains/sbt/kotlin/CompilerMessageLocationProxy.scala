package org.jetbrains.sbt.kotlin

import scala.language.reflectiveCalls

/**
 * Encapsulates reflective access to the Kotlin compiler message location class.
 */
class CompilerMessageLocationProxy(instance: AnyRef) {
  private type CompilerMessageLocation = {
    def getPath: String
    def getLine: Int
    def getColumn: Int
  }

  def path: String = instance.asInstanceOf[CompilerMessageLocation].getPath
  def line: Int = instance.asInstanceOf[CompilerMessageLocation].getLine
  def column: Int = instance.asInstanceOf[CompilerMessageLocation].getColumn
}
