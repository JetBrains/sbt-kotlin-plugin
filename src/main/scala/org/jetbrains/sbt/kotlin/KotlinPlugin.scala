package org.jetbrains.sbt.kotlin

import org.jetbrains.sbt.kotlin.Keys.*
import org.jetbrains.sbt.kotlin.PluginCompat.*
import sbt.Keys.*
import sbt.internal.inc.JarUtils
import sbt.internal.remotecache.{CompileRemoteCacheArtifact, TestRemoteCacheArtifact}
import sbt.plugins.JvmPlugin
import sbt.{Keys as _, *}

import java.nio.file.Paths

object KotlinPlugin extends AutoPlugin {
  override def trigger = noTrigger

  override def requires: Plugins = JvmPlugin

  override def projectConfigurations: Seq[Configuration] = Seq(KotlinInternal)

  private def kotlinScriptCompilerDeps(kotlinVer: String, provided: Boolean): Seq[ModuleID] = {
    if (KotlinVersion(kotlinVer) <= KotlinVersion("1.3.21")) {
      val kotlinScriptRuntime = "org.jetbrains.kotlin" % "kotlin-script-runtime" % kotlinVer
      val dependency = if (provided) kotlinScriptRuntime % Provided else kotlinScriptRuntime
      Seq(dependency)
    } else {
      val kotlinScriptingCompilerEmbeddable = "org.jetbrains.kotlin" % "kotlin-scripting-compiler-embeddable" % kotlinVer
      val dependency = if (provided) kotlinScriptingCompilerEmbeddable % Provided else kotlinScriptingCompilerEmbeddable
      Seq(
        kotlinScriptingCompilerEmbeddable % KotlinInternal.name,
        dependency
      )
    }
  }

  private final val KotlinModuleBackupDir = "_kotlin_module_backup_"
  private final val MetaInfDir = "META-INF"

  override def projectSettings: Seq[Setting[?]] = Seq(
    autoScalaLibrary := false,
    crossPaths := false,
    libraryDependencies ++= Seq(
      "org.jetbrains.kotlin" % "kotlin-compiler-embeddable" % kotlinVersion.value % KotlinInternal.name
    ) ++ kotlinScriptCompilerDeps(kotlinVersion.value, kotlinRuntimeProvided.value),
    KotlinInternal / managedClasspath := Def.uncached {
      managedJarsCompat(KotlinInternal, classpathTypes.value, update.value, fileConverter.value)
    },
    kotlinVersion := "1.3.50",
    kotlincJvmTarget := "1.6",
    kotlinRuntimeProvided := false,
    kotlincOptions := Nil,
    kotlincPluginOptions := Nil,
    watchSources ++= {
      val kotlinSources = "*.kt" || "*.kts"
      (Compile / sourceDirectories).value.flatMap(dir => (dir ** kotlinSources).get()) ++
        (Test / sourceDirectories).value.flatMap(dir => (dir ** kotlinSources).get())
    }
  ) ++ inConfig(Compile)(kotlinCompileSettings) ++
    inConfig(Test)(kotlinCompileSettings)

  //noinspection ScalaWeakerAccess
  // public to allow kotlin compile in other configs beyond Compile and Test
  val kotlinCompileSettings: Seq[Setting[?]] = Seq(
    unmanagedSourceDirectories += kotlinSource.value,
    unmanagedSources / includeFilter ~= { _ => ("*.java" | "*.kt" | "*.kts") },
    kotlincOptions := kotlincOptions.value,
    kotlincJvmTarget := kotlincJvmTarget.value,
    kotlinModuleName := {
      val name = moduleName.value
      val config = configuration.value match {
        case Compile => "main"
        case Test => "test"
        case c => c.name
      }
      s"$name.$config"
    },
    kotlincPluginOptions := kotlincPluginOptions.value,
    compileIncremental := Def.uncached(compileIncrementalTaskImpl.value),
    kotlinSource := sourceDirectory.value / "kotlin",

    // Compilation cache tasks
    packageCache := Def.uncached {
      val converter = fileConverter.value
      val outputDirectory = (packageCache / remoteCacheArtifact).value match {
        case artifact: CompileRemoteCacheArtifact => artifact.extractDirectory.toPath
        case artifact: TestRemoteCacheArtifact => artifact.extractDirectory.toPath
        case _ =>
          val converter = fileConverter.value
          converter.toPath(backendOutput.value)
      }
      val cacheArtifact = packageCache.value
      val cacheArtifactNioPath = PluginCompat.toNioPath(cacheArtifact, converter)
      val name = kotlinModuleName.value
      val kotlinModuleFileName = s"$name.kotlin_module"
      val moduleRelativePath = Paths.get(MetaInfDir, kotlinModuleFileName)
      JarUtils.removeFromJar(cacheArtifactNioPath, Seq(moduleRelativePath.toString))
      val moduleFile = outputDirectory.resolve(moduleRelativePath)
      val moduleBackupRelativePath = Paths.get(KotlinModuleBackupDir, kotlinModuleFileName)
      if (moduleFile.toFile.exists()) {
        JarUtils.includeInJar(cacheArtifactNioPath.toFile, Seq(moduleFile.toFile -> moduleBackupRelativePath.toString))
      }
      cacheArtifact
    },
    pullRemoteCache := Def.uncached {
      pullRemoteCache.value
      val outputDirectory = (packageCache / remoteCacheArtifact).value match {
        case artifact: CompileRemoteCacheArtifact => artifact.extractDirectory
        case artifact: TestRemoteCacheArtifact => artifact.extractDirectory
        case _ => classDirectory.value
      }
      val name = kotlinModuleName.value
      val kotlinModuleFileName = s"$name.kotlin_module"
      val moduleBackupDir = outputDirectory / KotlinModuleBackupDir
      val src = moduleBackupDir / kotlinModuleFileName
      val dst = outputDirectory / MetaInfDir / kotlinModuleFileName
      if (src.exists()) {
        IO.move(src, dst)
        IO.delete(moduleBackupDir)
      }
    }
  )
}
