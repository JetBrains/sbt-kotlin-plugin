package kotlin

import kotlin.Keys.*
import sbt.Keys.*
import sbt.plugins.JvmPlugin
import sbt.{Def, Keys as _, *}

/**
 * @author pfnguyen
 */
object KotlinPlugin extends AutoPlugin {
  override def trigger = noTrigger

  override def requires: Plugins = JvmPlugin

  override def projectConfigurations: Seq[Configuration] = KotlinInternal :: Nil

  private def kotlinScriptCompilerDeps(kotlinVer: String, provided: Boolean) = {
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

  override def projectSettings: Seq[Def.Setting[?]] = Seq(
    libraryDependencies ++= Seq(
      "org.jetbrains.kotlin" % "kotlin-compiler-embeddable" % kotlinVersion.value % KotlinInternal.name
    ) ++ kotlinScriptCompilerDeps(kotlinVersion.value, kotlinRuntimeProvided.value),
    KotlinInternal / managedClasspath := Classpaths.managedJars(KotlinInternal, classpathTypes.value, update.value),
    kotlinVersion := "1.3.50",
    kotlincJvmTarget := "1.6",
    kotlinRuntimeProvided := false,
    kotlincOptions := Nil,
    kotlincPluginOptions := Nil,
    watchSources ++= {
      import language.postfixOps
      val kotlinSources = "*.kt" || "*.kts"
      (Compile / sourceDirectories).value.flatMap(_ ** kotlinSources get) ++
        (Test / sourceDirectories).value.flatMap(_ ** kotlinSources get)
    }
  ) ++ inConfig(Compile)(kotlinCompileSettings) ++
    inConfig(Test)(kotlinCompileSettings)

  //noinspection ScalaWeakerAccess
  // public to allow kotlin compile in other configs beyond Compile and Test
  val kotlinCompileSettings = List(
    unmanagedSourceDirectories += kotlinSource.value,
    unmanagedSources ++= {
      import language.postfixOps
      val kotlinSources = "*.kt" || "*.kts"
      sourceDirectories.value.flatMap(_ ** kotlinSources get)
    },
    kotlincOptions := kotlincOptions.value,
    kotlincJvmTarget := kotlincJvmTarget.value,
    kotlincPluginOptions := kotlincPluginOptions.value,
    compileIncremental := KotlinCompile.compileTask.value,
    kotlinSource := sourceDirectory.value / "kotlin"
  )
}
