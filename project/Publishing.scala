import sbt.Keys.*
import sbt.{Credentials, Def, Path, fileToRichFile, toRepositoryName}

object Publishing {
  private val loadCredentialsFromSystemPropsOrFile = credentials += {
    val fromSystemProps = for {
      user <- sys.props.get("scala_plugin_deps_user")
      pwd  <- sys.props.get("scala_plugin_deps_password")
    } yield Credentials(realm = null, host = "packages.jetbrains.team", userName = user, passwd = pwd)

    fromSystemProps
      .getOrElse(Credentials(Path.userHome / ".sbt" / ".scala_plugin_deps_credentials"))
  }

  private val publishIfNotSnapshot = publish := {
    if (isSnapshot.value)
      sys.error("Do not publish snapshot versions. Set a tag 'vX.Y.Z' to proceed. Example: v3.20.2")
    else
      publish.value
  }

  val settings: Seq[Def.Setting[?]] = Seq(
    publishTo     := Some("Scala Plugin Dependencies" at "https://packages.jetbrains.team/maven/p/scl/scala-plugin-deps"),
    versionScheme := Some("semver-spec"),
    loadCredentialsFromSystemPropsOrFile,
    publishIfNotSnapshot,
  )
}
