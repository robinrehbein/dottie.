import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import de.robinrehbein.punkt.game.animations.ExplosionAnimation
import de.robinrehbein.punkt.game.animations.GameAnimation
import de.robinrehbein.punkt.game.animations.Particle
import kotlin.random.Random

object AnimationFactory {

    fun createPerfectHitAnimation(position: Offset): List<GameAnimation> {
        return listOf(
            ExplosionAnimation(position = position, particles = createGoldenParticles(position))
        )
    }

    fun createGoodHitAnimation(position: Offset): List<GameAnimation> {
        return listOf(
            ExplosionAnimation(position = position, particles = createRainbowParticles(position))
        )
    }

    private fun createGoldenParticles(position: Offset): List<Particle> {
        return (0..20).map {
            val angle = (it * 18f) * (Math.PI / 180f)
            val speed = Random.nextFloat() * 300f + 20000f
            val velocityX = (Math.cos(angle) * speed).toFloat()
            val velocityY = (Math.sin(angle) * speed).toFloat() - 200f

            Particle(
                startX = position.x,
                startY = position.y,
                velocityX = velocityX,
                velocityY = velocityY,
                color = when (Random.nextInt(4)) {
                    0 -> Color.Yellow
                    1 -> Color(0xFFFFD700) // Gold
                    else -> Color(0xFFFFA500) // Orange
                },
                size = Random.nextFloat() * 6f + 3f
            )
        }
    }

    private fun createRainbowParticles(position: Offset): List<Particle> {
        val colors = listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Blue,
            Color.Magenta
        )

        return (0..20).map {
            val angle = (it * 18f) * (Math.PI / 180f)
            val speed = Random.nextFloat() * 300f + 20000f
            val velocityX = (Math.cos(angle) * speed).toFloat()
            val velocityY = (Math.sin(angle) * speed).toFloat()

            Particle(
                startX = position.x,
                startY = position.y,
                velocityX = velocityX,
                velocityY = velocityY,
                color = colors.random(),
                size = Random.nextFloat() * 10f + 6f
            )
        }
    }
}