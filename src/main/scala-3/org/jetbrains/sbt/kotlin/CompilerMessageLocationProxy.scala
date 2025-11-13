package org.jetbrains.sbt.kotlin

import scala.reflect.Selectable.reflectiveSelectable

private class CompilerMessageLocationProxy(instance: AnyRef):
  private type CompilerMessageLocation = {
    def getPath: String
    def getLine: Int
    def getColumn: Int
  }

  def path: String = instance.asInstanceOf[CompilerMessageLocation].getPath
  def line: Int = instance.asInstanceOf[CompilerMessageLocation].getLine
  def column: Int = instance.asInstanceOf[CompilerMessageLocation].getColumn
