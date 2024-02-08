package kotlin

import sbt.{Def, *}

/**
 * @author pfnguyen
 */
object Keys {
  val Kotlin = config("kotlin")
  val KotlinInternal = config("kotlin-internal").hide

  val kotlinCompile = TaskKey[Unit]("kotlin-compile",
    "runs kotlin compilation, occurs before normal compilation")
  val kotlincPluginOptions = TaskKey[Seq[String]]("kotlinc-plugin-options",
    "kotlin compiler plugin options")
  val kotlinSource = SettingKey[File]("kotlin-source", "kotlin source directory")
  val kotlinVersion = SettingKey[String]("kotlin-version",
    "version of kotlin to use for building")
  val kotlincOptions = SettingKey[Seq[String]]("kotlinc-options",
    "options to pass to the kotlin compiler")
  val kotlincJvmTarget = SettingKey[String]("kotlinc-jvm-target",
    "jvm target to use for building")

  val kotlinRuntimeProvided = SettingKey[Boolean]("kotlin-stdlib-provided",
    "Mark Kotlin runtime library dependencies as Provided, useful for IntelliJ IDEA plugins where Kotlin standard libraries are included in the platform")

  def kotlinLib(name: String): Def.Setting[Seq[ModuleID]] = sbt.Keys.libraryDependencies +=
    "org.jetbrains.kotlin" % ("kotlin-" + name) % kotlinVersion.value

  def kotlinPlugin(name: String): Def.Setting[Seq[ModuleID]] = sbt.Keys.libraryDependencies +=
    "org.jetbrains.kotlin" % ("kotlin-" + name) % kotlinVersion.value % "compile-internal"

  def kotlinClasspath(config: Configuration, classpathKey: Def.Initialize[sbt.Keys.Classpath]): Setting[?] =
    config / kotlincOptions ++= {
      "-cp" ::
        classpathKey.value.map(_.data.getAbsolutePath).mkString(java.io.File.pathSeparator) ::
        Nil
    }

  case class KotlinPluginOptions(pluginId: String) {
    def option(key: String, value: String) =
      s"plugin:$pluginId:$key=$value"
  }
}
