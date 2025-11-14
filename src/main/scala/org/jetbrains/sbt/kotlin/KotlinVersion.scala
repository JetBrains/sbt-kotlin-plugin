package org.jetbrains.sbt.kotlin

import scala.math.Ordered.orderingToOrdered
import scala.util.matching.Regex

// based on https://github.com/JetBrains/intellij-community/blob/c8ba8401f73488f966b5ed5b5c5afaa38a9bcbdf/plugins/kotlin/base/plugin/src/org/jetbrains/kotlin/idea/compiler/configuration/IdeKotlinVersion.kt#L24
final class KotlinVersion private[kotlin](val major: Int, val minor: Int, val patch: Int,
                                          val kindSuffix: KotlinVersion.Kind,
                                          val buildNumber: Option[String]) extends Ordered[KotlinVersion] {
  override def compare(that: KotlinVersion): Int = {
    val base = (major, minor, patch).compare((that.major, that.minor, that.patch))
    if (base != 0) base
    else {
      val kind = kindSuffix.compareTo(that.kindSuffix)
      if (kind != 0) kind else buildNumber.compare(that.buildNumber)
    }
  }

  override def equals(that: Any): Boolean = that match {
    case version: KotlinVersion => compare(version) == 0
    case _ => false
  }
}

object KotlinVersion {
  private val kotlinVersionRegex: Regex =
    new Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([A-Za-z]\\w+(?:\\.\\d+)?(?:-release)?))?(?:-(\\d+)?)?$", "major", "minor", "patch", "kindSuffix", "buildNumber")

  private val IdeBuildRegex = "ij\\d+(?:\\.\\d+)?".r

  def apply(versionString: String): KotlinVersion = versionString match {
    case kotlinVersionRegex(majorStr, minorStr, patchStr, kindSuffixStr, buildNumberStr) =>
      val majorValue = majorStr.parseVersionComponent("major")
      val minorValue = minorStr.parseVersionComponent("minor")
      val patchValue = patchStr.parseVersionComponent("patch")

      val kindSuffixOpt = Option(kindSuffixStr).map(_.toLowerCase) match {
        case None | Some("release") =>
          Some(Kind.Release)
        case Some("dev") =>
          Some(Kind.Dev)
        case Some("snapshot") | Some("local") =>
          Some(Kind.Snapshot)
        case Some(suffix) if suffix.startsWith("rc") =>
          parseKind(suffix, "rc")(Kind.ReleaseCandidate.apply)
        case Some(suffix) if suffix.startsWith("beta") =>
          parseKind(suffix, "beta")(Kind.Beta.apply)
        case Some(suffix) if suffix.startsWith("eap") =>
          parseKind(suffix, "eap")(Kind.Eap.apply)
        case Some(suffix) if suffix.startsWith("m") =>
          parseKind(suffix, "m")(Kind.Milestone.apply)
        case Some(suffix) if suffix.matches(IdeBuildRegex.regex) =>
          val parts = suffix.stripPrefix("ij").split('.').toSeq.map(_.toInt)
          Some(Kind.ForIde(suffix, parts))
        case _ => None
      }
      val kindSuffix = kindSuffixOpt.getOrElse(throw new IllegalArgumentException(s"""Unsupported version kind suffix: "$kindSuffixStr" ($versionString)"""))

      val buildNumber = Option(buildNumberStr).filterNot(_.isEmpty)

      new KotlinVersion(majorValue, minorValue, patchValue, kindSuffix, buildNumber)
    case _ => throw new IllegalArgumentException(s"Unsupported Kotlin version: $versionString")
  }

  private def parseKind(kindSuffix: String, prefix: String)(constructor: Option[Int] => Kind): Option[Kind] = {
    assert(kindSuffix.startsWith(prefix), s"""Prefix "$prefix" not found in kind suffix "$kindSuffix"""")

    val numberString = kindSuffix.drop(prefix.length).stripSuffix("-release")
    if (numberString.isEmpty) Some(constructor(None))
    else {
      val number = numberString.toIntOption
      if (number.isDefined) Some(constructor(number)) else None
    }
  }

  private final implicit class StringOps(private val s: String) extends AnyVal {
    def parseVersionComponent(name: String): Int =
      Option(s).flatMap(_.toIntOption) match {
        case Some(value) => value
        case None => throw new IllegalArgumentException(s"Invalid $name version component: $s")
      }

    def toIntOption: Option[Int] = scala.util.Try(s.toInt).toOption
  }

  sealed abstract class Kind(val artifactSuffix: String) extends Comparable[Kind] {
    override def toString: String = getClass.getSimpleName

    override def compareTo(that: Kind): Int = (this, that) match {
      // same kinds
      case (Kind.Release, Kind.Release) =>
        0
      case (Kind.ReleaseCandidate(num1), Kind.ReleaseCandidate(num2)) =>
        num1.compareTo(num2)
      case (Kind.Beta(num1), Kind.Beta(num2)) =>
        num1.compareTo(num2)
      case (Kind.Eap(num1), Kind.Eap(num2)) =>
        num1.compareTo(num2)
      case (Kind.Milestone(num1), Kind.Milestone(num2)) =>
        if (num1.isEmpty && num2.contains(1) || num2.isEmpty && num1.contains(1)) 0
        else num1.compareTo(num2)
      case (Kind.ForIde(_, parts1), Kind.ForIde(_, parts2)) =>
        import PluginCompat.seqDerivedOrdering
        parts1.compareTo(parts2)
      case (Kind.Dev, Kind.Dev) =>
        0
      case (Kind.Snapshot, Kind.Snapshot) =>
        0

      // different kinds: Snapshot < Milestone < Eap < Beta < ReleaseCandidate < Release < Dev < ForIde
      case (Kind.Snapshot, _)            => -1
      case (_, Kind.Snapshot)            =>  1
      case (Kind.Milestone(_), _)        => -1
      case (_, Kind.Milestone(_))        =>  1
      case (Kind.Eap(_), _)              => -1
      case (_, Kind.Eap(_))              =>  1
      case (Kind.Beta(_), _)             => -1
      case (_, Kind.Beta(_))             =>  1
      case (Kind.ReleaseCandidate(_), _) => -1
      case (_, Kind.ReleaseCandidate(_)) =>  1
      case (Kind.Release, _)             => -1
      case (_, Kind.Release)             =>  1
      case (Kind.Dev, _)                 => -1
      case (_, Kind.Dev)                 =>  1
    }
  }

  object Kind {
    case object Release extends Kind(artifactSuffix = null)
    case class ReleaseCandidate(number: Option[Int]) extends Kind(artifactSuffix = "RC" + number.getOrElse(""))
    case class Beta(number: Option[Int]) extends Kind(artifactSuffix = "Beta" + number.getOrElse(""))
    case class Eap(number: Option[Int]) extends Kind(artifactSuffix = "eap" + number.getOrElse(""))
    // M should always have a number, so default to M1
    case class Milestone(number: Option[Int]) extends Kind(artifactSuffix = number.fold("M1")("M" + _))
    case class ForIde(platform: String, parts: Seq[Int]) extends Kind(artifactSuffix = platform)
    case object Dev extends Kind(artifactSuffix = "dev")
    case object Snapshot extends Kind(artifactSuffix = "SNAPSHOT")
  }
}
