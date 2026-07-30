import sbt.Keys.*
import sbt.{Def, Developer, ScmInfo, uri, given}

object Publishing {

  //Reminder: sonatype namespace will be inherited from ThisBuild / organization
  val settings: Seq[Def.Setting[?]] = Seq(
    versionScheme := Some("semver-spec"),

    // Optional but nice-to-have
    organizationName := "JetBrains",
    organizationHomepage := Some(uri("https://www.jetbrains.com/")),

    licenses += ("MIT", uri("https://opensource.org/license/mit/")),

    homepage := Some(uri("https://github.com/JetBrains/sbt-kotlin-plugin")),

    developers := List(
      Developer(
        id = "JetBrains",
        name = "JetBrains",
        email = "scala-developers@jetbrains.com",
        url = uri("https://github.com/JetBrains")
      )
    ),

    scmInfo := Some(
      ScmInfo(
        uri("https://github.com/JetBrains/sbt-kotlin-plugin"),
        "scm:git:git@github.com:JetBrains/sbt-kotlin-plugin.git",
        "scm:git:git@github.com:JetBrains/sbt-kotlin-plugin.git"
      )
    ),
  )
}
