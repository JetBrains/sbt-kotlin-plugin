import sbt.Def
import sbt.Keys.version
import sbt.ScriptedPlugin.autoImport.scriptedLaunchOpts

object Scripted {
  private val SbtIvyHome = "sbt.ivy.home"

  private val passPluginVersion =
    scriptedLaunchOpts += "-Dplugin.version=" + version.value

  private val forwardIvyHomeProperty =
    scriptedLaunchOpts ++= sys.props.get(SbtIvyHome)
      .map(ivyHome => s"-D$SbtIvyHome=$ivyHome")

  val settings: Seq[Def.Setting[?]] = Seq(
    passPluginVersion,
    forwardIvyHomeProperty,
  )
}
