import org.jetbrains.sbt.kotlin.Keys.*

name := "kotlin-2.4-compat"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "2.4.0"
kotlincJvmTarget := "17"
