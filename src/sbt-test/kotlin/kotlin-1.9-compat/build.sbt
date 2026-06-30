import org.jetbrains.sbt.kotlin.Keys.*

name := "kotlin-1.9-compat"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "1.9.20"
kotlincJvmTarget := "1.8"
