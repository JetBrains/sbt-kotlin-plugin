package org.jetbrains.sbt.kotlin

import scala.language.higherKinds

private[kotlin] object OrderingImplicitsCompat:
  given seqDerivedOrdering: [CC[X] <: scala.collection.Seq[X], T] => Ordering[T] => Ordering[CC[T]] =
    scala.math.Ordering.Implicits.seqOrdering
