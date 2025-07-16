import sbt.Keys.*
import sbt.{Def, ScmInfo, url}

object Publishing {

  val settings: Seq[Def.Setting[?]] = Seq(
    versionScheme := Some("semver-spec"),

    scmInfo := Some(
      ScmInfo(
        url("https://github.com/JetBrains/sbt-kotlin-plugin"),
        "scm:git:git@github.com:JetBrains/sbt-kotlin-plugin.git",
        "scm:git:git@github.com:JetBrains/sbt-kotlin-plugin.git"
      )
    ),
  )
}
