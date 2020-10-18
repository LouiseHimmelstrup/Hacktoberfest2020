import com.soywiz.kds.IntArray2
import com.soywiz.klock.milliseconds
import com.soywiz.korev.Key
import com.soywiz.korge.*
import com.soywiz.korge.input.keys
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

suspend fun main() = Korge(width = 1600, height = 896, bgcolor = Colors["#2b2b2b"]) {
    val playerIdle = SpriteAnimation(
            listOf(
                    resourcesVfs["chars2/Female/Poses/female_idle.png"].readBitmap().slice()
            )
    )
    val playerWalk = SpriteAnimation(
            listOf(
                resourcesVfs["chars2/Female/Poses/female_walk1.png"].readBitmap().slice(),
                resourcesVfs["chars2/Female/Poses/female_walk2.png"].readBitmap().slice(),
            ),
            200.milliseconds
    )

    val platforms = resourcesVfs["platforms/tilesheet_complete.png"].readBitmap().slice().split(BLOCK_SIZE, BLOCK_SIZE)
    var playerX = 0.0
    var playerY = 12.0
    val cam = Camera(0.0, 0.0, width, height)
    val container = cameraContainer(width, height) {
        theMap.each { x, y, v ->
            val tile = when (v) {
                SKY -> {
                    solidRect(BLOCK_SIZE, BLOCK_SIZE, Colors.ALICEBLUE)
                }

                TOP -> {
                    image(platforms[2])
                }

                GROUND -> {
                    image(platforms[0])
                }

                else -> error("???")
            }

            tile.xy(x * BLOCK_SIZE, y * BLOCK_SIZE)
        }


        val playerView = sprite(playerIdle)


        var walking = Walking.IDLE
        playerView.addUpdater { dt ->
            when(walking) {
                Walking.IDLE -> {
                    playerView.playAnimation(playerIdle)
                }
                Walking.RIGHT -> {
                    playerView.playAnimation(playerWalk)
                    playerX += dt.seconds
                    scaleX = 1.0
                }
                Walking.LEFT -> {
                    playerView.playAnimation(playerWalk)
                    scaleX = -1.0
                    playerX -= dt.seconds
                }
            }

            xy(playerX * BLOCK_SIZE, playerY * BLOCK_SIZE - playerView.height)
        }

        playerView.keys {
            down(Key.D) {
                walking = Walking.RIGHT
            }

            down(Key.A) {
                walking = Walking.LEFT
            }
        }
    }


    container.camera = cam
    addUpdater {
        cam.x = (playerX - 6.0) * BLOCK_SIZE
    }
}
