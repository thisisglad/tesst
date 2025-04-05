package kr.toxicity.hud.pack

import kr.toxicity.hud.api.plugin.ReloadFlagType
import kr.toxicity.hud.api.plugin.ReloadInfo
import kr.toxicity.hud.manager.ConfigManagerImpl
import kr.toxicity.hud.util.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.text.DecimalFormat
import java.util.*
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class PackType {
    FOLDER {
        @Volatile
        private var beforeByte = 0L
        private inner class FileTreeBuilder(
            private val info: ReloadInfo
        ) : Builder() {
            private val build = DATA_FOLDER.parentFile.subFolder(ConfigManagerImpl.buildFolderLocation)
            private val locationMap = TreeMap<String, File>(Comparator.reverseOrder())
            private val skipIo = info.has(ReloadFlagType.PREVENT_GENERATE_RESOURCE_PACK) && build.isNotEmptyDirectory()

            init {
                if (!skipIo) {
                    fun getAllLocation(file: File, length: Int) {
                        locationMap.put(file.path.substring(length), file)?.let {
                            info.sender.warn("Duplicated file skipped: ${file.path} and ${it.path}")
                        }
                        file.forEach {
                            getAllLocation(it, length)
                        }
                    }
                    build.forEach {
                        getAllLocation(it, build.path.length + 1)
                    }
                }
            }

            fun save(packFile: PackFile) {
                val arr = packFile()
                synchronized(this) {
                    byte += arr.size
                    byteArrayMap[packFile.path] = arr
                }
                if (skipIo) return
                val replace = packFile.path.replace('/', File.separatorChar)
                (synchronized(locationMap) {
                    locationMap.remove(replace)
                } ?: File(build, replace).apply {
                    parentFile.mkdirs()
                }).outputStream().buffered().use { stream ->
                    stream.write(arr)
                }
            }

            fun close() {
                if (skipIo) return
                synchronized(this) {
                    if (ConfigManagerImpl.clearBuildFolder) {
                        val iterator = locationMap.iterator()
                        while (iterator.hasNext()) {
                            val (path, next) = iterator.next()
                            if (!path.startsWith("assets/${ConfigManagerImpl.namespace}") || next.listFiles()?.isNotEmpty() == true) continue
                            next.delete()
                        }
                    }
                    info("File packed: ${if (beforeByte > 0) "${mbFormat(beforeByte)} -> ${mbFormat(byte)}" else mbFormat(byte)}")
                    if (beforeByte != byte) {
                        beforeByte = byte
                    }
                }
            }
        }

        override fun createGenerator(meta: PackMeta, info: ReloadInfo): Generator {
            val builder = FileTreeBuilder(info)
            return object : Generator {
                override val resourcePack: Map<String, ByteArray>
                    get() = Collections.unmodifiableMap(builder.byteArrayMap)

                override fun close() {
                    if (PackUploader.stop()) info("Resource pack host is stopped.")
                    builder.close()
                }
                override fun invoke(p1: PackFile) {
                    builder.save(p1)
                }
            }
        }
    },
    ZIP {
        @Volatile
        private var beforeByte = 0L

        private inner class ZipBuilder(
            val zip: ZipOutputStream
        ) : Builder()

        override fun createGenerator(meta: PackMeta, info: ReloadInfo): Generator {
            val protection = ConfigManagerImpl.enableProtection
            val host = ConfigManagerImpl.enableSelfHost
            val message = runCatching {
                MessageDigest.getInstance("SHA-1")
            }.getOrNull()
            val file = File(DATA_FOLDER.parentFile, "${ConfigManagerImpl.buildFolderLocation}.zip")
            beforeByte = file.length()
            val stream = ByteArrayOutputStream()
            val zip = ZipBuilder(ZipOutputStream(stream.buffered()).apply {
                setComment("BetterHud resource pack.")
                setLevel(Deflater.BEST_COMPRESSION)
            })
            fun addEntry(entry: ZipEntry, byte: ByteArray) {
                synchronized(zip) {
                    runCatching {
                        zip.byteArrayMap[entry.name] = byte
                        zip.zip.putNextEntry(entry)
                        zip.zip.write(byte)
                        zip.zip.closeEntry()
                        if (protection) {
                            entry.crc = byte.size.toLong()
                            entry.size = BigInteger(byte).mod(BigInteger.valueOf(Long.MAX_VALUE)).toLong()
                        }
                    }.onFailure {
                        it.handle(info.sender, "Unable to write this file: ${entry.name}")
                    }
                }
            }
            if (host) {
                BOOTSTRAP.resource("icon.png")?.buffered()?.use {
                    addEntry(ZipEntry("pack.png"), it.readAllBytes())
                }
                addEntry(PackMeta.zipEntry, meta.toByteArray())
            }
            return object : Generator {
                override val resourcePack: Map<String, ByteArray>
                    get() = Collections.unmodifiableMap(zip.byteArrayMap)

                override fun close() {
                    if (message == null) return warn("Unable to find SHA-1 algorithm, skipped.")
                    synchronized(zip) {
                        zip.zip.close()
                        val finalByte = stream.toByteArray()
                        info(
                                "File packed: ${if (beforeByte > 0) "${mbFormat(beforeByte)} -> ${mbFormat(finalByte.size.toLong())}" else mbFormat(finalByte.size.toLong())}",
                        )
                        var previousUUID = PackUUID.previous
                        if (previousUUID == null || ConfigManagerImpl.forceUpdate || beforeByte != finalByte.size.toLong() || info.has(ReloadFlagType.FORCE_GENERATE_RESOURCE_PACK)) {
                            beforeByte = finalByte.size.toLong()
                            DigestOutputStream(file.outputStream(), message).buffered().use {
                                it.write(finalByte)
                            }
                            previousUUID = PackUUID.from(message)
                            info(
                                "File zipped: ${mbFormat(file.length())}"
                            )
                        }
                        if (host) {
                            PackUploader.upload(previousUUID , file.inputStream().buffered().use {
                                it.readAllBytes()
                            })
                        } else previousUUID.save()
                    }
                }

                override fun invoke(p1: PackFile) {
                    val entry = ZipEntry(p1.path)
                    val byte = p1()
                    addEntry(entry, byte)
                }
            }
        }
    },
    NONE {
        override fun createGenerator(meta: PackMeta, info: ReloadInfo): Generator {
            val builder = Builder()
            return object : Generator {
                override val resourcePack: Map<String, ByteArray>
                    get() = Collections.unmodifiableMap(builder.byteArrayMap)

                override fun close() {
                    if (PackUploader.stop()) info("Resource pack host is stopped.")
                }

                override fun invoke(p1: PackFile) {
                    val byte = p1()
                    synchronized(builder) {
                        builder.byte += byte.size
                        builder.byteArrayMap[p1.path] = byte
                    }
                }
            }
        }
    },
    ;
    companion object {
        private val decimal = DecimalFormat("#,###.###")

        private fun mbFormat(long: Long): String {
            return "${decimal.format(BigDecimal("${long}.000") / BigDecimal("1048576.000"))}MB"
        }
    }

    private open class Builder {
        @Volatile
        var byte = 0L
        val byteArrayMap = HashMap<String, ByteArray>()
    }
    
    abstract fun createGenerator(meta: PackMeta, info: ReloadInfo): Generator
    
    interface Generator : (PackFile) -> Unit, AutoCloseable {
        val resourcePack: Map<String, ByteArray>
    }
}