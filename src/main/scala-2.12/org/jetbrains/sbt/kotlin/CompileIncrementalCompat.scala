package org.jetbrains.sbt.kotlin

import org.jetbrains.sbt.kotlin.Keys.*
import sbt.*
import sbt.Keys.*
import xsbti.compile.CompileResult

private[kotlin] object CompileIncrementalCompat {
  def compileIncrementalTaskImpl: Def.Initialize[Task[CompileResult]] = Def.task {
    KotlinCompile.compileIncremental(
      streams = streams.value,
      inputs = (compile / compileInputs).value,
      converter = fileConverter.value,
      kotlinVersion = kotlinVersion.value,
      kotlincOptions = kotlincOptions.value,
      kotlincJvmTarget = kotlincJvmTarget.value,
      kotlinModuleName = kotlinModuleName.value,
      kotlincPluginOptions = kotlincPluginOptions.value,
      classpathOptions = classpathOptions.value,
      compilerClasspath = (KotlinInternal / managedClasspath).value
    )
  }.tag(Tags.Compile, Tags.CPU)
}
