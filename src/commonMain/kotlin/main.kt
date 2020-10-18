import com.soywiz.kds.IntArray2
import com.soywiz.klock.DateTime
import com.soywiz.klock.TimeSpan
import com.soywiz.klock.milliseconds
import com.soywiz.klock.seconds
import com.soywiz.korev.Key
import com.soywiz.korge.*
import com.soywiz.korge.annotations.KorgeExperimental
import com.soywiz.korge.view.*
import com.soywiz.korge.view.camera.Camera
import com.soywiz.korge.view.camera.cameraContainer
import com.soywiz.korim.bitmap.slice
import com.soywiz.korim.color.*
import com.soywiz.korim.format.*
import com.soywiz.korio.file.std.*

const val BLOCK_SIZE = 64

const val SKY = 0
const val TOP = 1
const val GROUND = 2

enum class Walking {
    LEFT,
    RIGHT,
    IDLE
}

sealed class Jumping {
    class IsJumping(val start: DateTime) : Jumping()
    object NotJumping : Jumping()
}

val theMap = IntArray2(
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
        SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS
        SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS
        SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS
        TTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTSS
        GGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGSS
    """.trimIndent(),
    default = 0,
    transform = mapOf(
        'S' to SKY,
        'T' to TOP,
        'G' to GROUND
    )
)

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

@OptIn(KorgeExperimental::class)
suspend fun main() = Korge(width = 1600, height = 896, bgcolor = Colors["#2b2b2b"]) {
    val charPoses = "chars2/Female/Poses"
    val playerIdle = animFromBitmaps(charPoses, "female_idle")
    val playerWalk = animFromBitmaps(charPoses, "female_walk1", "female_walk2", animTime = 200.milliseconds)
    val playerJump = animFromBitmaps(charPoses, "female_jump")
    val platforms = resourcesVfs["platforms/tilesheet_complete.png"].readBitmap().slice().split(BLOCK_SIZE, BLOCK_SIZE)

    val cam = Camera(0.0, 0.0, width, height)
    cameraContainer(width, height) {
        theMap.each { x, y, v ->
            val tile = when (v) {
                SKY -> solidRect(BLOCK_SIZE, BLOCK_SIZE, Colors.ALICEBLUE)
                TOP -> image(platforms[2])
                GROUND -> image(platforms[0])
                else -> error("Unknown tile $v")
            }

            tile.xy(x * BLOCK_SIZE, y * BLOCK_SIZE)

            // Quick and easy fix to avoid tearing between tiles
            tile.scaleX = 1.008
            tile.scaleY = 1.008
        }

        val playerView = sprite(playerIdle).centered

        var jumping: Jumping = Jumping.NotJumping
        var walking: Walking
        var playerX = 0.0
        var playerY = 12.0

        playerView.addUpdater { dt ->
            walking = when {
                views.keys[Key.D] -> Walking.RIGHT
                views.keys[Key.A] -> Walking.LEFT
                else -> Walking.IDLE
            }

            val cJumping = jumping
            if (views.keys[Key.SPACE] && jumping !is Jumping.IsJumping) {
                jumping = Jumping.IsJumping(views.timeProvider.now())
            }

            if (cJumping is Jumping.IsJumping) {
                if (views.timeProvider.now() - cJumping.start > 2.seconds) {
                    jumping = Jumping.NotJumping
                }
            }

            when (walking) {
                Walking.IDLE -> {
                    playerView.playAnimation(playerIdle)
                }
                Walking.RIGHT -> {
                    playerView.playAnimation(playerWalk)
                    playerX += dt.seconds * 3
                    scaleX = 1.0
                }
                Walking.LEFT -> {
                    playerView.playAnimation(playerWalk)
                    scaleX = -1.0
                    playerX -= dt.seconds * 3
                }
            }

            when (jumping) {
                is Jumping.IsJumping -> {
                    playerView.playAnimation(playerJump)
                }

                Jumping.NotJumping -> {
                    // Do nothing
                }
            }

            cam.x = (playerX - 6.0) * BLOCK_SIZE
            xy(playerX * BLOCK_SIZE + (playerView.width / 2.0), playerY * BLOCK_SIZE - (playerView.height / 2.0))
        }
    }.apply {
        camera = cam
    }
}
