package kotlin

import kotlin.Keys.kotlinModuleName
import sbt.*
import sbt.Keys.{classDirectory, packageCache, pullRemoteCache}
import sbt.internal.inc.JarUtils

import java.nio.file.Paths

private[kotlin] object RemoteCache {
  private final val KotlinModuleBackupDir = "_kotlin_module_backup_"
  private final val MetaInfDir = "META-INF"

  def packageCacheTask: Def.Initialize[Task[File]] = Def.task {
    val cacheArtifact = packageCache.value
    val name = kotlinModuleName.value
    val kotlinModuleFileName = s"$name.kotlin_module"
    val moduleRelativePath = Paths.get(MetaInfDir, kotlinModuleFileName)
    JarUtils.removeFromJar(cacheArtifact.toPath, Seq(moduleRelativePath.toString))
    val moduleFile = classDirectory.value.toPath.resolve(moduleRelativePath)
    val moduleBackupRelativePath = Paths.get(KotlinModuleBackupDir, kotlinModuleFileName)
    if (moduleFile.toFile.exists()) {
      JarUtils.includeInJar(cacheArtifact, Seq(moduleFile.toFile -> moduleBackupRelativePath.toString))
    }
    cacheArtifact
  }

  def pullRemoteCacheTask: Def.Initialize[Task[Unit]] = Def.task {
    pullRemoteCache.value
    val name = kotlinModuleName.value
    val kotlinModuleFileName = s"$name.kotlin_module"
    val moduleBackupDir = classDirectory.value / KotlinModuleBackupDir
    val src = moduleBackupDir / kotlinModuleFileName
    val dst = classDirectory.value / MetaInfDir / kotlinModuleFileName
    if (src.exists()) {
      IO.move(src, dst)
      IO.delete(moduleBackupDir)
    }
  }
}
