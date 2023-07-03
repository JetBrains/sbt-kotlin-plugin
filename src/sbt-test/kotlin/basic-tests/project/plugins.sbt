{
  val ver = System.getProperty("plugin.version")
  if (ver == null)
    throw new RuntimeException("""
                                 |The system property 'plugin.version' is not defined.
                                 |Specify this property using scriptedLaunchOpts -Dplugin.version."""
      .stripMargin)
  else addSbtPlugin("org.jetbrains.scala" % "sbt-kotlin-plugin" % ver)
}