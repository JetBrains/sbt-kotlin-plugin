package kotlin

case class KotlinVersion(versionString: String) extends AnyVal

object KotlinVersion {
  implicit val versionOrdering: Ordering[KotlinVersion] =
    Ordering.by { _.versionString.split("[.-]").map(_.toInt).toIterable }
}
