import com.soywiz.kds.IntArray2
import com.soywiz.korge.render.RenderContext
import com.soywiz.korge.view.*
import com.soywiz.korim.bitmap.Bitmap
import com.soywiz.korim.bitmap.BitmapSlice
import com.soywiz.korim.bitmap.slice
import com.soywiz.korim.format.readBitmap
import com.soywiz.korio.file.std.resourcesVfs
import kotlin.properties.Delegates
import com.soywiz.korim.color.Colors
import kotlin.math.roundToInt

enum class Tile(
    val id: Char? = null,
    val coordinate: IntPair? = null,
    val isSolid: Boolean = false,
    val transparent: Boolean = false,
    val customRender: ((container: Container) -> View)? = null
) {
    SKY('S', customRender = { c ->
        c.solidRect(TILE_SIZE, TILE_SIZE, Colors.ALICEBLUE)
    }),
    TOP('T', IntPair(2, 0), isSolid = true),
    GROUND('G', IntPair(0, 0), isSolid = true),
    LEVER_LEFT('L', IntPair(9, 10), isSolid = false, transparent = true),
    LEVER_RIGHT(coordinate = IntPair(11, 10), isSolid = false, transparent = true)
}

fun isBlockSolid(block: Int): Boolean {
    return Tile.values()[block].isSolid
}

const val TILE_SIZE = 64

typealias LoadedTileSheet = List<BitmapSlice<Bitmap>>

class Tiles {
    var cols: Int = -1
    var rows: Int = -1

    var platforms: LoadedTileSheet by Delegates.notNull()

    suspend fun load() {
        val bitmap = resourcesVfs["platforms/tilesheet_complete.png"].readBitmap()
        cols = bitmap.width / TILE_SIZE
        rows = bitmap.height / TILE_SIZE
        platforms = bitmap.slice().split(TILE_SIZE, TILE_SIZE)
    }
}

class TileMap(private val tiles: Tiles, map: IntArray2) : Container() {
    private val views = Array<Array<View?>>(map.width) { arrayOfNulls(map.height) }
    val map = Array(map.width) { Array(map.height) { Tile.SKY } }

    val worldWidth = map.width
    val worldHeight = map.height

    init {
        val descriptors = Tile.values()
        map.each { x, y, v ->
            val tileDescriptor = descriptors[v]
            setTile(x, y, tileDescriptor)
        }
    }

    fun replaceTile(x: Int, y: Int, newTile: Tile) {
        views[x][y]?.removeFromParent()
        setTile(x, y, newTile)
    }

    fun setTile(x: Int, y: Int, tileDescriptor: Tile) {
        if (tileDescriptor.transparent) {
            val tile = solidRect(TILE_SIZE, TILE_SIZE, Colors.ALICEBLUE)
            tile.xy(x * TILE_SIZE, y * TILE_SIZE)

            // Quick and easy fix to avoid tearing between tiles
            tile.scaleX = 1.008
            tile.scaleY = 1.008
        }

        val tile = when {
            tileDescriptor.coordinate != null -> {
                image(
                    tiles.platforms[
                            tileDescriptor.coordinate.x % tiles.cols + tileDescriptor.coordinate.y * tiles.cols
                    ]
                )
            }

            tileDescriptor.customRender != null -> {
                tileDescriptor.customRender.invoke(this)
            }

            else -> {
                error("Unable to render tile descriptor: $tileDescriptor")
            }
        }

        tile.xy(x * TILE_SIZE, y * TILE_SIZE)

        // Quick and easy fix to avoid tearing between tiles
        tile.scaleX = 1.008
        tile.scaleY = 1.008

        views[x][y] = tile
        map[x][y] = tileDescriptor
    }

    fun getTile(x: Int, y: Int): Tile {
        return map[x][y]
    }

    inline fun attemptInteraction(condition: Boolean, tile: Tile, x: Double, y: Double, handler: (IntPair) -> Unit): Boolean {
        if (!condition) return false
        val baseX = x.roundToInt()
        val baseY = y.roundToInt()

        if (getTile(baseX, baseY - 1) == tile) {
            handler(IntPair(baseX, baseY - 1))
            return true
        }
        if (getTile(baseX + 1, baseY - 1) == tile) {
            handler(IntPair(baseX + 1, baseY - 1))
            return true
        }
        if (getTile(baseX - 1, baseY - 1) == tile) {
            handler(IntPair(baseX - 1, baseY - 1))
            return true
        }
        return false
    }
}
