import org.jetbrains.sbt.kotlin.Keys.*

name := "kotlin-1.2-compat"

enablePlugins(KotlinPlugin)
kotlinLib("stdlib")

kotlinVersion := "1.2.71"
