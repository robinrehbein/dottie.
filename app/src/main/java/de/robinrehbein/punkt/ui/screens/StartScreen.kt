package de.robinrehbein.punkt.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.robinrehbein.punkt.R
import de.robinrehbein.punkt.game.models.GameMode
import de.robinrehbein.punkt.ui.components.PixelButton
import de.robinrehbein.punkt.ui.theme.PunktTheme

/**
 * Pixelify Sans FontFamily for consistent typography
 */
private val Bytesized = FontFamily(
    Font(R.font.bytesized, FontWeight.Normal)
)

/**
 * StartScreen composable that displays the game's main menu with a pixel art start button
 *
 * @param onStartGame Callback function to be invoked when the start game button is clicked
 * @param selectedGameMode Currently selected game mode
 * @param onGameModeChanged Callback function to be invoked when the game mode is changed
 * @param modifier Modifier to be applied to the screen
 */
@Composable
fun StartScreen(
    onStartGame: () -> Unit,
    selectedGameMode: GameMode,
    onGameModeChanged: (GameMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)) // Dark blue background similar to game
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Game Title
            Text(
                text = "PUNKT.",
                fontFamily = Bytesized,
                fontWeight = FontWeight.Normal,
                fontSize = 48.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Subtitle
            Text(
                text = "Das Timing-Spiel",
                fontFamily = Bytesized,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                color = Color(0xFFB0B0B0),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Gamemode Selector
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                // Left Arrow
                PixelButton(
                    text = "<",
                    onClick = {
                        val modes = GameMode.values()
                        val currentIndex = modes.indexOf(selectedGameMode)
                        val previousIndex = if (currentIndex > 0) currentIndex - 1 else modes.size - 1
                        onGameModeChanged(modes[previousIndex])
                    },
                    width = 40.dp,
                    height = 40.dp,
                    borderWidth = 3.dp,
                    backgroundColor = Color(0xFFE8B4E8),
                    borderColor = Color(0xFF5555FF),
                    textColor = Color(0xFF5555FF)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Current Mode Name
                Text(
                    text = selectedGameMode.displayName,
                    color = Color.White,
                    fontFamily = Bytesized,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(120.dp)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Right Arrow
                PixelButton(
                    text = ">",
                    onClick = {
                        val modes = GameMode.values()
                        val currentIndex = modes.indexOf(selectedGameMode)
                        val nextIndex = if (currentIndex < modes.size - 1) currentIndex + 1 else 0
                        onGameModeChanged(modes[nextIndex])
                    },
                    width = 40.dp,
                    height = 40.dp,
                    borderWidth = 3.dp,
                    backgroundColor = Color(0xFFE8B4E8),
                    borderColor = Color(0xFF5555FF),
                    textColor = Color(0xFF5555FF)
                )
            }
            
            // Start Game Button using PixelArtButton
            PixelButton(
                text = "SPIEL STARTEN",
                onClick = onStartGame,
                backgroundColor = Color(0xFFE8B4E8), // Light pink/purple
                borderColor = Color(0xFF5555FF), // Blue/purple
                textColor = Color(0xFF5555FF),
                width = 240.dp,
                height = 70.dp,
                borderWidth = 5.dp
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Instructions
            Text(
                text = "Tippe zur richtigen Zeit auf den Punkt!",
                fontFamily = Bytesized,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                color = Color(0xFF888888),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * Preview of the StartScreen
 */
@Preview(showBackground = true)
@Composable
private fun StartScreenPreview() {
    PunktTheme {
        StartScreen(
            onStartGame = { /* Preview action */ },
            selectedGameMode = GameMode.CLASSIC,
            onGameModeChanged = { /* Preview action */ }
        )
    }
}