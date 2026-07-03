package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.navigation.NavHostController
import com.example.viewmodel.ChatViewModel

@Composable
fun ActiveGameScreen(
    viewModel: ChatViewModel,
    navController: NavHostController,
    gameId: String,
    mode: String,
    peerId: String,
    difficulty: String
) {
    var isPaused by remember { mutableStateOf(false) }

    BackHandler(enabled = !isPaused) {
        isPaused = true
    }
    
    val defaultColors = MaterialTheme.colorScheme
    val gameColorScheme = remember(gameId) {
        when (gameId) {
            "chess" -> darkColorScheme(
                background = androidx.compose.ui.graphics.Color(0xFF1a1a2e),
                surface = androidx.compose.ui.graphics.Color(0xFF16213e),
                surfaceVariant = androidx.compose.ui.graphics.Color(0xFF0f3460),
                primary = androidx.compose.ui.graphics.Color(0xFFe94560),
                onSurface = androidx.compose.ui.graphics.Color.White,
                onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFa0a0b0)
            )
            "snake" -> darkColorScheme(
                background = androidx.compose.ui.graphics.Color(0xFF0b1f0b),
                surface = androidx.compose.ui.graphics.Color(0xFF132e13),
                surfaceVariant = androidx.compose.ui.graphics.Color(0xFF1c421c),
                primary = androidx.compose.ui.graphics.Color(0xFF00ff00),
                onSurface = androidx.compose.ui.graphics.Color.White,
                onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF88cc88)
            )
            "pingpong" -> darkColorScheme(
                background = androidx.compose.ui.graphics.Color(0xFF12001c),
                surface = androidx.compose.ui.graphics.Color(0xFF240038),
                surfaceVariant = androidx.compose.ui.graphics.Color(0xFF3b0059),
                primary = androidx.compose.ui.graphics.Color(0xFFcc00ff),
                onSurface = androidx.compose.ui.graphics.Color.White,
                onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFdca3ff)
            )
            else -> defaultColors
        }
    }

    MaterialTheme(colorScheme = gameColorScheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // App Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        IconButton(onClick = { isPaused = true }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = gameId.uppercase(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Mode: $mode | Diff: $difficulty",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Game Surface
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .background(
                           brush = androidx.compose.ui.graphics.Brush.linearGradient(
                               colors = listOf(
                                   MaterialTheme.colorScheme.surfaceVariant,
                                   MaterialTheme.colorScheme.background
                               )
                           ),
                           shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val isHost = viewModel.myNodeId < peerId
                    when (gameId) {
                        "dino" -> DinoGameView(viewModel, mode, peerId, isHost, isPaused, difficulty)
                        "pingpong" -> PingPongGameView(viewModel, mode, peerId, isHost, isPaused, difficulty)
                        "chess" -> ChessGameView(viewModel, mode, peerId, isHost, difficulty)
                        "snake" -> SnakeGameView(viewModel, mode, peerId, isHost, isPaused, difficulty)
                        "cards" -> CardsGameView(viewModel, mode, peerId, isHost, difficulty)
                        else -> Text("Unknown Game")
                    }
                }
            }

            if (isPaused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {}, // Intentionally consume clicks
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.width(300.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Game Paused", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Your session state is preserved.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { isPaused = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Resume")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Exit to Menu", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceholderGameView(name: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Icon(Icons.Filled.SportsEsports, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("$name Game Engine", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Waiting for remote peer or running local logic...", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
