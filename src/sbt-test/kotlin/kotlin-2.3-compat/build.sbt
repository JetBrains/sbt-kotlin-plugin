import org.jetbrains.sbt.kotlin.Keys.*

name := "kotlin-2.3-compat"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "2.3.10"
kotlincJvmTarget := "1.8"
