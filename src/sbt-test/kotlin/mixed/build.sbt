import org.jetbrains.sbt.kotlin.Keys.*

name := "mixed"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlincOptions += "-verbose"
