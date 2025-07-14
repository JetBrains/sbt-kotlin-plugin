import sbt.Keys.*
import sbt.internal.sona
import sbt.librarymanagement.ivy.Credentials
import sbt.{Def, DirectCredentials, ScmInfo, url}

object Publishing {

  val SonatypeRepoName = "Sonatype Nexus Repository Manager"

  private val publishIfNotSnapshot = publish := {
    if (isSnapshot.value)
      sys.error("Do not publish snapshot versions. Set a tag 'vX.Y.Z' to proceed. Example: v3.20.2")
    else
      publish.value
  }

  val settings: Seq[Def.Setting[?]] = Seq(
    versionScheme := Some("semver-spec"),
    publishIfNotSnapshot,
    
    // Central Portal configuration - sbt-ci-release 1.11.0 defaults to Central Portal
    // No explicit sonatypeCredentialHost setting needed for Central Portal
    
    // Required for sbt plugins with Central Portal
    sbtPluginPublishLegacyMavenStyle := false,
    
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/JetBrains/sbt-kotlin-plugin"),
        "scm:git:git@github.com:JetBrains/sbt-kotlin-plugin.git",
        "scm:git:git@github.com:JetBrains/sbt-kotlin-plugin.git"
      )
    ),

    // Overwrite/filter-out existing credentials
    // Use copy of `sbt.internal.SysProp.sonatypeCredentalsEnv` but with custom environment variables
    credentials := credentials.value.filter {
      case c: DirectCredentials => c.realm != SonatypeRepoName
      case _ => true
    } ++ {
      val env = sys.env.get(_)
      for {
        username <- env("SONATYPE_USERNAME_NEW")
        password <- env("SONATYPE_PASSWORD_NEW")
      } yield Credentials(
        SonatypeRepoName,
        sona.Sona.host,
        username,
        password
      )
    },
  )
}
