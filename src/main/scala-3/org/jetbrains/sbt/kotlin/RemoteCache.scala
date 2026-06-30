package org.jetbrains.sbt.kotlin

import sbt.Setting

private[kotlin] object RemoteCache:
  def remoteCacheSettings: Seq[Setting[?]] = Seq.empty
