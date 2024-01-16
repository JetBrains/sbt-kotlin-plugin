import sbt.{Def, ScmInfo, url}
import sbt.Keys.*
import xerial.sbt.Sonatype.GitHubHosting
import xerial.sbt.Sonatype.autoImport.sonatypeProjectHosting

object Publishing {
  private val publishIfNotSnapshot = publish := {
    if (isSnapshot.value)
      sys.error("Do not publish snapshot versions. Set a tag 'vX.Y.Z' to proceed. Example: v3.20.2")
    else
      publish.value
  }

  val settings: Seq[Def.Setting[?]] = Seq(
    sonatypeProjectHosting := Some(GitHubHosting("JetBrains", "sbt-kotlin-plugin", "scala-developers@jetbrains.com")),
    versionScheme := Some("semver-spec"),
    publishIfNotSnapshot,
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/JetBrains/sbt-kotlin-plugin"),
        "scm:git:git@github.com:JetBrains/sbt-kotlin-plugin.git",
        "scm:git:git@github.com:JetBrains/sbt-kotlin-plugin.git"
      )
    )
  )
}
