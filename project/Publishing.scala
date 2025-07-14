import sbt.Keys.*
import sbt.internal.sona
import sbt.librarymanagement.ivy.Credentials
import sbt.{Def, DirectCredentials, ScmInfo, url}

object Publishing {

  private val SonatypeRepoName = "Sonatype Nexus Repository Manager"

  val settings: Seq[Def.Setting[?]] = Seq(
    versionScheme := Some("semver-spec"),

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
