import sbt.Keys.*
import sbt.{Def, Developer, ScmInfo, url}

object Publishing {

  //Reminder: sonatype namespace will be inherited from ThisBuild / organization
  val settings: Seq[Def.Setting[?]] = Seq(
    versionScheme := Some("semver-spec"),

    // Optional but nice-to-have
    organizationName := "JetBrains",
    organizationHomepage := Some(url("https://www.jetbrains.com/")),

    licenses += ("MIT", url("https://opensource.org/license/mit/")),

    homepage := Some(url("https://github.com/JetBrains/sbt-kotlin-plugin")),

    developers := List(
      Developer(
        id = "JetBrains",
        name = "JetBrains",
        email = "scala-developers@jetbrains.com",
        url = url("https://github.com/JetBrains")
      )
    ),

    scmInfo := Some(
      ScmInfo(
        url("https://github.com/JetBrains/sbt-kotlin-plugin"),
        "scm:git:git@github.com:JetBrains/sbt-kotlin-plugin.git",
        "scm:git:git@github.com:JetBrains/sbt-kotlin-plugin.git"
      )
    ),
  )
}
