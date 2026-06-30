import org.jetbrains.sbt.kotlin.Keys.*

name := "kotlin-script"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "1.3.21"
