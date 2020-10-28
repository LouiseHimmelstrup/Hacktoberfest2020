import com.soywiz.kds.IntArray2
import com.soywiz.klock.*
import com.soywiz.kmem.toIntCeil
import com.soywiz.kmem.toIntFloor
import com.soywiz.korev.Key
import com.soywiz.korge.*
import com.soywiz.korge.annotations.KorgeExperimental
import com.soywiz.korge.view.*
import com.soywiz.korge.view.camera.Camera
import com.soywiz.korge.view.camera.cameraContainer
import com.soywiz.korim.bitmap.slice
import com.soywiz.korim.color.*
import com.soywiz.korim.font.readBitmapFont
import com.soywiz.korim.format.*
import com.soywiz.korio.file.std.*
import kotlin.math.max
import kotlin.math.min
import kotlin.properties.Delegates

enum class Walking {
    LEFT,
    RIGHT,
    IDLE
}

sealed class Jumping {
    class IsJumping(val start: DateTime) : Jumping()
    object NotJumping : Jumping()
}

val map1 = IntArray2(
    """
        SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS
        SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS
        SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS
        SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS
        SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS
        SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS
        SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS
        SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS
        SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS
        SSSSTGSSSSSSSSSSSSSSSSSSTTSSSSSSSSSTSS
        SSSSSSSSSSSSSSSSSTTSSSSSSSSSSSSSSSSGSS
        SSSSSLSSSTTSTTTSSSSSSLLLSSSSSSSSSSSGSS
        TGTGTGTGTGTGTGTGTGTGTGTGTGTGTGTGTGTGTG
        GGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGSS
    """.trimIndent(),
    gen = { c, x, y ->
        Tile.values().find { it.id == c }?.ordinal ?: error("Unknown tile '$c'")
    }
)

var currentMap: TileMap by Delegates.notNull()

suspend fun animFromBitmaps(
    basePath: String,
    vararg frames: String,
    extension: String = ".png",
    animTime: TimeSpan = TimeSpan.NIL,
): SpriteAnimation {
    return SpriteAnimation(
        frames.map { resourcesVfs["$basePath/$it$extension"].readBitmap().slice() },
        animTime
    )
}

data class IntPair(val x: Int, val y: Int) {
    override fun toString() = "($x, $y)"
}

class Entity(
    var worldX: Double,
    var worldY: Double,
    var sprite: Sprite,
    var animations: HashMap<String, SpriteAnimation>,
    var speedMultiplier: Double = 1.0,
    var walking: Walking = Walking.IDLE,
) {
    private var jumping: Jumping = Jumping.NotJumping
    var screenWidth = sprite.width
    var screenHeight = sprite.height
    var worldWidth = screenWidth / TILE_SIZE.toDouble()
    var worldHeight = screenHeight / TILE_SIZE.toDouble()

    var worldWidthCeil = worldWidth.toIntCeil()
    var worldHeightCeil = worldHeight.toIntCeil()

    var canJump = false

    // TODO This set of functions is extremely inconsistent
    @OptIn(ExperimentalStdlibApi::class)
    fun tilesInFrontOfMe(): Set<IntPair> {
        return buildSet {
            for (dy in 0 until worldHeightCeil) {
                // Tiny smudge factor introduced to avoid getting stuck on the ground
                val element = IntPair((worldX + worldWidth).toIntFloor(), (worldY - dy - 0.100).toIntFloor())
                if (element.x >= 0 && element.x < currentMap.worldWidth && element.y >= 0 && element.y < currentMap.worldHeight) {
                    add(element)
                }
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun tilesBehindMe(): Set<IntPair> {
        return buildSet {
            for (dy in 0 until worldHeightCeil) {
                // Tiny smudge factor introduced to avoid getting stuck on the ground
                val element = IntPair((worldX).toIntFloor(), (worldY - dy - 0.100).toIntFloor())
                if (element.x >= 0 && element.x < currentMap.worldWidth && element.y >= 0 && element.y < currentMap.worldHeight) {
                    add(element)
                }
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun tilesAboveMe(): Set<IntPair> {
        return buildSet {
            for (dx in 0 until worldWidthCeil) {
                val element = IntPair((worldX + dx).toIntFloor(), (worldY - worldHeightCeil).toIntFloor())
                if (element.x >= 0 && element.x < currentMap.worldWidth && element.y >= 0 && element.y < currentMap.worldHeight) {
                    add(element)
                }
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun tilesBelowMe(): Set<IntPair> {
        return buildSet {
            for (dx in 0 until worldWidthCeil) {
                val element = IntPair((worldX + dx).toIntFloor(), (worldY).toIntFloor())
                if (element.x >= 0 && element.x < currentMap.worldWidth && element.y >= 0 && element.y < currentMap.worldHeight) {
                    add(element)
                }
            }
        }
    }

    fun loop(dt: TimeSpan, views: Views) {
        var speedX = 0.0
        var speedY = 0.0
        when (walking) {
            Walking.IDLE -> {
                playAnimation(ANIM_IDLE)
                speedX = 0.0 * speedMultiplier
            }
            Walking.RIGHT -> {
                playAnimation(ANIM_WALK)

                if (tilesInFrontOfMe().none { currentMap.getTile(it.x, it.y).isSolid }) {
                    speedX = 3.0 * speedMultiplier
                    sprite.scaleX = 1.0
                }
            }
            Walking.LEFT -> {
                playAnimation(ANIM_WALK)

                if (tilesBehindMe().none { currentMap.getTile(it.x, it.y).isSolid }) {
                    speedX = -3.0 * speedMultiplier
                    sprite.scaleX = -1.0
                }
            }
        }

        val cJumping = jumping
        if (cJumping is Jumping.IsJumping) {
            if (views.timeProvider.now() - cJumping.start > 500.milliseconds) {
                jumping = Jumping.NotJumping
            }
        }

        when (cJumping) {
            is Jumping.IsJumping -> {
                playAnimation(ANIM_JUMP)
                if (tilesAboveMe().none { currentMap.getTile(it.x, it.y).isSolid }) {
                    speedY = -3.0
                }
            }

            Jumping.NotJumping -> {
                // Apply gravity
                if (tilesBelowMe().none { currentMap.getTile(it.x, it.y).isSolid }) {
                    speedY = 3.0
                    playAnimation(ANIM_FALL)
                    canJump = false
                } else {
                    canJump = true
                }
            }
        }

        worldX += speedX * dt.seconds
        worldY += speedY * dt.seconds

        run {
            if (speedX > 0) {
                val afterUpdate = tilesInFrontOfMe()
                if (afterUpdate.any { currentMap.getTile(it.x, it.y).isSolid }) {
                    val x = afterUpdate.first().x.toDouble()
                    worldX = min(worldX, x - 1)
                }
            }
        }

        run {
            if (speedX < 0) {
                val afterUpdate = tilesBehindMe()
                if (afterUpdate.any { currentMap.getTile(it.x, it.y).isSolid }) {
                    val x = afterUpdate.first().x.toDouble()
                    worldX = max(worldX, x)
                }
            }
        }

        run {
            if (speedY < 0) {
                val aboveAfterUpdate = tilesAboveMe()
                if (aboveAfterUpdate.any { currentMap.getTile(it.x, it.y).isSolid }) {
                    val toDouble = aboveAfterUpdate.first().y.toDouble()
                    worldY = max(worldY, toDouble + 1.0 + worldHeight)
                }
            }
        }

        run {
            if (speedY > 0) {
                val belowAfterUpdate = tilesBelowMe()
                if (belowAfterUpdate.any { currentMap.getTile(it.x, it.y).isSolid }) {
                    worldY = min(worldY, belowAfterUpdate.first().y.toDouble())
                }
            }
        }

        // Clamp coordinates to map
        if (worldX < 0) worldX = 0.0
        if (worldX > currentMap.width - 1.0) worldX = currentMap.width - 1.0
        if (worldY < 0) worldY = 0.0
        if (worldY > currentMap.height) worldY = currentMap.height

        sprite.xy(worldX * TILE_SIZE + (sprite.width / 2.0), worldY * TILE_SIZE - (sprite.height / 2.0))
    }

    fun jump(views: Views) {
        if (canJump) {
            jumping = Jumping.IsJumping(views.timeProvider.now())
            canJump = false
        }
    }
}

fun Entity.playAnimation(anim: String) {
    sprite.playAnimation(animations[anim] ?: return)
}

fun Entity.addToContainer(container: Container, views: Views) {
    container.addChild(sprite)
    playAnimation(ANIM_IDLE)

    sprite.addUpdater { dt ->
        loop(dt, views)
    }
}

fun Entity.addZombieAI(views: Views) {
    var last = views.timeProvider.now() - 10.hours
    sprite.addUpdater { dt ->
        val now = views.timeProvider.now()
        if (now - last > 5.seconds) {
            walking = Walking.values().random()
            last = now
        }
    }
}

const val ANIM_IDLE = "idle"
const val ANIM_WALK = "walk"
const val ANIM_JUMP = "jump"
const val ANIM_FALL = "fall"

var _debugText = "Debug"

fun addDebug(debug: String) {
    _debugText += debug + "\n"
}

@OptIn(KorgeExperimental::class)
suspend fun main() = Korge(width = 1600, height = 896, bgcolor = Colors["#2b2b2b"]) {
    val tiles = Tiles()
    tiles.load()

    val cam = Camera(0.0, 0.0, width, height)
    cameraContainer(width, height) {
        addChild(TileMap(tiles, map1).also { currentMap = it })

        val player = run {
            val charPoses = "chars2/Female/Poses"
            val playerIdle = animFromBitmaps(charPoses, "female_idle")
            val playerWalk = animFromBitmaps(charPoses, "female_walk1", "female_walk2", animTime = 200.milliseconds)
            val playerJump = animFromBitmaps(charPoses, "female_jump")
            val playerFall = animFromBitmaps(charPoses, "female_fall")

            Entity(
                0.0,
                11.990,
                Sprite(playerIdle).centered,
                hashMapOf(
                    ANIM_IDLE to playerIdle,
                    ANIM_WALK to playerWalk,
                    ANIM_JUMP to playerJump,
                    ANIM_FALL to playerFall
                )
            )
        }

        val zombieAnimations = run {
            val poses = "chars2/Zombie/Poses"
            val idle = animFromBitmaps(poses, "zombie_idle")
            val walk = animFromBitmaps(poses, "zombie_walk1", "zombie_walk2", animTime = 200.milliseconds)
            val jump = animFromBitmaps(poses, "zombie_jump")
            val fall = animFromBitmaps(poses, "zombie_fall")

            hashMapOf(
                ANIM_IDLE to idle,
                ANIM_WALK to walk,
                ANIM_JUMP to jump,
                ANIM_FALL to fall
            )
        }

        val testZombie = Entity(
            10.0,
            5.0,
            Sprite(zombieAnimations[ANIM_IDLE]!!).centered,
            zombieAnimations,
            speedMultiplier = 2.0
        )

        player.addToContainer(this, views)
        testZombie.addToContainer(this, views)
        testZombie.addZombieAI(views)

        with(player) {
            var nextInteractionAllowedAt = views.timeProvider.now()
            sprite.addUpdater { dt ->
                val now = views.timeProvider.now()
                walking = when {
                    views.keys[Key.D] -> Walking.RIGHT
                    views.keys[Key.A] -> Walking.LEFT
                    else -> Walking.IDLE
                }

                if (views.keys[Key.SPACE]) {
                    jump(views)
                }

                if (now > nextInteractionAllowedAt) {
                    var didInteract = false

                    fun interact(tile: Tile, handler: (IntPair) -> Unit) {
                        didInteract = didInteract || currentMap.attemptInteraction(
                            views.keys[Key.E],
                            tile,
                            worldX,
                            worldY,
                            handler
                        )
                    }

                    interact(Tile.LEVER_LEFT) { currentMap.replaceTile(it.x, it.y, Tile.LEVER_RIGHT) }
                    interact(Tile.LEVER_RIGHT) { currentMap.replaceTile(it.x, it.y, Tile.LEVER_LEFT) }

                    if (didInteract) nextInteractionAllowedAt = now + 500.milliseconds
                }

                addDebug("PlayerWorldX: $worldX, PlayerWorldY: $worldY")
                cam.x = (worldX - 6.0) * TILE_SIZE
            }
        }

        addUpdater { addDebug("FPS: ${views.gameWindow.fps}") }
    }.apply {
        camera = cam
    }

    run {
        val font = resourcesVfs["clear_sans.fnt"].readBitmapFont()
        val bg = solidRect(0, 0, Colors.BLACK).xy(50, 50)

        text(_debugText, font = font, color = Colors.WHITE, textSize = 20.0).xy(bg.x + 5, bg.y + 5).addUpdater {
            text = _debugText.removeSuffix("\n")
            _debugText = ""
            bg.width = width + 10.0
            bg.height = height + 10.0
        }
    }
}
