import org.jetbrains.sbt.kotlin.Keys.*

name := "kotlin-1.3-compat"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "1.3.41"
