import org.jetbrains.sbt.kotlin.Keys._

name := "mixed"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlincOptions += "-verbose"
