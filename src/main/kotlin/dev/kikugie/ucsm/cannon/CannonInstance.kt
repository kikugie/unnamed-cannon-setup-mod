package dev.kikugie.ucsm.cannon

import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import dev.kikugie.ucsm.UCSM
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap
import jk.tree.KDTree
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3i
import java.nio.file.Path
import kotlin.io.path.notExists
import kotlin.io.path.readLines

class CannonInstance(val file: Path, vals: Sequence<Pair<String, String>>) {
    val tree = KDTree<String>()
    val extras = Object2ObjectAVLTreeMap<String, String>()

    var origin: Vec3i? = null
    var direction: Direction? = null
    var mirrored: Boolean? = null

    init {
        vals.forEach { (pt, ct) ->
            if (coords.matchEntire(pt) != null)
                addTree(pt, ct)
            else extras[pt] = ct
        }
    }

    private fun addTree(pt: String, ct: String) {
        val (x, y, z) = pt.split(',', limit = 3)
        try {
            tree.addPoint(doubleArrayOf(x.toDouble(), y.toDouble(), z.toDouble()), ct)
        } catch (ignored: Exception) {
        }
    }

    fun setProperites(pos: BlockPos, facing: Direction, mirror: Boolean) {
        origin = pos
        direction = facing
        mirrored = mirror
    }

    companion object {
        val coords = Regex("(-?\\d+\\.\\d+),(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)")
        val LOAD_ERROR = DynamicCommandExceptionType { file -> Text.of("File not found: $file") }

        fun loadDir(dir: Path): CannonInstance {
            val root = MinecraftClient.getInstance().runDirectory.toPath()
            val ct = dir.resolve("Ct.txt").also {
                if (it.notExists()) throw LOAD_ERROR.create(root.relativize(it))
            }
            val pt = dir.resolve("Pt.txt").also {
                if (it.notExists()) throw LOAD_ERROR.create(root.relativize(it))
            }
            return CannonInstance(dir, pt.readLines().asSequence().zip(ct.readLines().asSequence())).also {
                UCSM.cannonCache[dir] = it
            }
        }

        fun lazyLoad(dir: Path): CannonInstance {
            return UCSM.cannonCache[dir] ?: loadDir(dir)
        }
    }
}