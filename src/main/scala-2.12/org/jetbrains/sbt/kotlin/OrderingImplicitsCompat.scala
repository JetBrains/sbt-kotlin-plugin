package org.jetbrains.sbt.kotlin

import scala.language.higherKinds

private[kotlin] object OrderingImplicitsCompat {
  implicit def seqDerivedOrdering[CC[X] <: scala.collection.Seq[X], T](implicit ord: Ordering[T]): Ordering[CC[T]] =
    scala.math.Ordering.Implicits.seqDerivedOrdering
}
