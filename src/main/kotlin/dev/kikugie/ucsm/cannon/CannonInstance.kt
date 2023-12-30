package dev.kikugie.ucsm.cannon

import dev.kikugie.ucsm.UCSM
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap
import jk.tree.KDTree
import jk.tree.KDTree.SearchResult
import kotlinx.coroutines.*
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.name
import kotlin.io.path.useLines

class CannonInstance(val file: Path) {
    private lateinit var tree: KDTree<String>
    private lateinit var extras: Object2ObjectAVLTreeMap<String, String>
    private var locked = AtomicBoolean(false)
    private val _loaded = AtomicBoolean(false)
    private var process: Deferred<Unit>? = null
    val loaded: Boolean
        get() = _loaded.get()

    var origin: Vec3i? = null
    var direction: Direction? = null
    var mirrored: Boolean? = null

    val configs = file.resolve("Ct.txt")
    val points = file.resolve("Pt.txt")

    init {
        reload()
    }

    fun getExtrasSafe(): Map<String, String>? = if (loaded) {
        extras
    } else null


    fun getCoord(pos: Vec3d): SearchResult<String>? = if (loaded) {
        tree.nearestNeighbour(pos)
    } else {
        sendError("This cannon is not loaded yet")
        null
    }


    fun getExtra(str: String): String? = if (loaded) {
        extras[str]
    } else {
        sendError("This cannon is not loaded yet")
        null
    }

    fun unload() {
        process?.cancel()
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun reload() {
        if (locked.get()) {
            sendError("This cannon is already loading")
            return
        }

        locked.set(true)
        _loaded.set(false)
        tree = KDTree<String>()
        extras = Object2ObjectAVLTreeMap<String, String>()
        process = GlobalScope.async {
            try {
                points.useLines { pts ->
                    configs.useLines { cts ->
                        pts.zip(cts).forEach { (pt, ct) ->
                            if (addTree(pt, ct)) extras[pt] = ct
                        }
                    }
                }
                _loaded.set(true)
                sendMessage("Cannon \"${file.name}\" is loaded")
            } catch (e: Exception) {
                sendError(e.message ?: "Failed to load cannon instance: $file")
            }
            locked.set(false)
        }
    }

    private fun addTree(pt: String, ct: String): Boolean {
        return try {
            val (x, y, z) = pt.split(',')
                .takeIf { it.size == 3 } ?: return true
            tree.addPoint(doubleArrayOf(x.toDouble(), y.toDouble(), z.toDouble()), ct)
            false
        } catch (ignored: Exception) {
            true
        }
    }

    fun setProperties(pos: BlockPos, facing: Direction, mirror: Boolean) {
        origin = pos
        direction = facing
        mirrored = mirror
    }

    private fun sendError(message: String) {
        MinecraftClient.getInstance().player?.sendMessage(Text.of("§4$message")) ?: UCSM.LOGGER.error(message)
    }

    private fun sendMessage(message: String) {
        MinecraftClient.getInstance().player?.sendMessage(Text.of("§a$message")) ?: UCSM.LOGGER.info(message)
    }

    companion object {
        fun lazyLoad(dir: Path): CannonInstance {
            return UCSM.cannonCache.computeIfAbsent(dir) { CannonInstance(it) }
        }
    }
}