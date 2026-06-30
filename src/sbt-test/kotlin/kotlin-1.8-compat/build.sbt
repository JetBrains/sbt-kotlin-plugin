import org.jetbrains.sbt.kotlin.Keys.*

name := "kotlin-1.8-compat"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "1.8.22"
kotlincJvmTarget := "1.8"
