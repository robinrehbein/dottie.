package de.robinrehbein.punkt.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeSceneContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import java.io.File
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface

/**
 * Eine headless Szene wie im ScreenshotRenderer, die zusätzlich ihre
 * Semantik verrät: Knöpfe und Kacheln werden über ihre Beschriftung
 * gefunden und in ihrer Mitte angetippt, statt über abgezählte Pixel.
 * Für die Sammlung (AP-15), deren Raster je nach Bildgröße anders fällt.
 */
@OptIn(InternalComposeUiApi::class)
internal class ProbeScene(
    val width: Int,
    val height: Int,
    density: Float,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) : AutoCloseable {

    private val owners = mutableListOf<SemanticsOwner>()
    private val surface = Surface.makeRasterN32Premul(width, height)
    private var now = 0L

    private val scene = run {
        val listener = object : PlatformContext.SemanticsOwnerListener {
            override fun onSemanticsOwnerAppended(semanticsOwner: SemanticsOwner) {
                owners += semanticsOwner
            }
            override fun onSemanticsOwnerRemoved(semanticsOwner: SemanticsOwner) {
                owners -= semanticsOwner
            }
            override fun onSemanticsChange(semanticsOwner: SemanticsOwner) {}
            override fun onLayoutChange(semanticsOwner: SemanticsOwner, semanticsNodeId: Int) {}
        }
        val platform = object : PlatformContext by PlatformContext.Empty {
            override val semanticsOwnerListener = listener
        }
        val context = object : ComposeSceneContext {
            override val platformContext = platform
        }
        CanvasLayersComposeScene(
            Density(density, fontScale),
            LayoutDirection.Ltr,
            IntSize(width, height),
            Dispatchers.Unconfined,
            context,
            {}
        )
    }

    init {
        scene.setContent(content)
        render()
    }

    private fun render() {
        surface.canvas.clear(0)
        scene.render(surface.canvas.asComposeCanvas(), now)
    }

    fun step(seconds: Double) {
        val until = now + (seconds * 1_000_000_000L).toLong()
        while (now < until) {
            now += 16_000_000L
            render()
        }
    }

    /** Alle antippbaren Knoten mit Beschriftung und Rolle. */
    fun clickables(): List<Triple<String, Role?, SemanticsNode>> {
        val out = mutableListOf<Triple<String, Role?, SemanticsNode>>()
        fun firstText(node: SemanticsNode): String? =
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
                ?: node.children.firstNotNullOfOrNull { firstText(it) }
        fun walk(node: SemanticsNode) {
            if (node.config.getOrNull(SemanticsActions.OnClick) != null) {
                val label = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                    ?: firstText(node)
                    ?: "?"
                out += Triple(label, node.config.getOrNull(SemanticsProperties.Role), node)
            }
            node.children.forEach { walk(it) }
        }
        owners.forEach { walk(it.unmergedRootSemanticsNode) }
        return out
    }

    /** Der antippbare Knoten, dessen Beschriftung mit [label] beginnt. */
    fun find(label: String): SemanticsNode? =
        clickables().firstOrNull { it.first == label || it.first.startsWith("$label,") }?.third
            ?: clickables().firstOrNull { it.first.startsWith(label) }?.third

    fun labels(): List<String> = clickables().map { it.first }

    fun tap(label: String) {
        val node = find(label) ?: error("nichts zum Tippen: „$label“ in ${labels()}")
        tapAt(node.boundsInRoot.center)
    }

    fun tapAt(position: Offset) {
        scene.sendPointerEvent(PointerEventType.Press, position)
        step(0.05)
        scene.sendPointerEvent(PointerEventType.Release, position)
        step(0.05)
    }

    fun scroll(position: Offset, dy: Float, times: Int) {
        repeat(times) {
            scene.sendPointerEvent(PointerEventType.Scroll, position, scrollDelta = Offset(0f, dy))
            step(0.05)
        }
    }

    fun save(file: File) {
        file.parentFile?.mkdirs()
        val data = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!
        file.writeBytes(data.bytes)
        println("-> ${file.name}")
    }

    override fun close() {
        scene.close()
    }
}
