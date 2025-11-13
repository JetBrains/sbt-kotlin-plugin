import sbt.internal.util.complete.Parsers.spaceDelimited

/**
 * ''NOTE'': Modify `src/sbt-test/kotlin/kotlin-x.y-compat/src/main/kotlin/demo/simple.kt` file to actually test compatibility.
 */
lazy val generateCompatTestSkeleton = inputKey[Unit]("Generates a compatibility test skeleton for a given Kotlin version (x.y or x.y.z).")

generateCompatTestSkeleton := {
  val log = streams.value.log
  val versionRegex = "^(\\d+\\.\\d+)(\\.\\d+)?$".r

  spaceDelimited("<arg>").parsed.headOption match {
    case Some(versionRegex(version, patch)) =>
      val fullVersion = version + Option(patch).getOrElse(".0")
      log.info(s"Generating compatibility test skeleton for Kotlin $version ($fullVersion)")

      val baseDir = (Compile / baseDirectory).value / "src" / "sbt-test" / "kotlin" / s"kotlin-$version-compat"

      IO.write(baseDir / "build.sbt",
        s"""import kotlin.Keys._
           |
           |kotlinLib("stdlib")
           |
           |kotlinVersion := "$fullVersion"
           |
           |val listClasses = taskKey[Unit]("listClasses")
           |
           |listClasses := {
           |  val classes = (Compile / backendOutput).value.listFiles()
           |  streams.value.log.info("classes: " + classes.mkString("Array(", ", ", ")"))
           |}
           |""".stripMargin)

      IO.write(baseDir / "test",
        """> compile
          |> listClasses
          |$ exists target/scala-2.12/classes/demo/SimpleKt.class
          |$ exists target/scala-2.12/classes/SimpleScript.class
          |""".stripMargin)

      IO.write(baseDir / "project" / "plugins.sbt",
        // Note: `s` interpolator is needed to print multiline string correctly
        s"""sys.props.get("plugin.version") match {
           |  case Some(version) => addSbtPlugin("org.jetbrains.scala" % "sbt-kotlin-plugin" % version)
           |  case _ => sys.error(
           |    ""\"The system property 'plugin.version' is not defined.
           |      |Specify this property using the scriptedLaunchOpts -Dplugin.version.""\".stripMargin)
           |}
           |""".stripMargin)

      val baseSourceDir = baseDir / "src" / "main" / "kotlin"

      IO.write(baseSourceDir / "demo" / "simple.kt",
        s"""package demo
           |
           |fun main(args: Array<String>) {
           |    // Test some Kotlin $version features
           |}
           |""".stripMargin)

      IO.write(baseSourceDir / "SimpleScript.kts",
        s"""println("Hello world!")
           |""".stripMargin)

      log.info(s"Generated test directory: $baseDir")
    case _ => log.error("no version. examples are 1.8 or 1.8.22")
  }
}
