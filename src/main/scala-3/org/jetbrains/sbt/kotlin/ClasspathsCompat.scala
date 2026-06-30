package org.jetbrains.sbt.kotlin

import sbt.Keys.Classpath
import sbt.{Classpaths, Configuration, UpdateReport}
import xsbti.FileConverter

private[kotlin] object ClasspathsCompat:
  def managedJars(config: Configuration, jarTypes: Set[String], up: UpdateReport)
    (using converter: FileConverter): Classpath =
    Classpaths.managedJars(config, jarTypes, up, converter)
