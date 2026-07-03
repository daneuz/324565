package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.example.viewmodel.ChatViewModel

data class GameOption(val id: String, val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

val AVAILABLE_GAMES = listOf(
    GameOption("chess", "Chess", Icons.Filled.Dashboard),
    GameOption("pingpong", "Ping Pong", Icons.Filled.SportsTennis),
    @Suppress("DEPRECATION")
    GameOption("dino", "Dinosaur Run", Icons.Filled.DirectionsRun),
    GameOption("snake", "Snake", Icons.Filled.Gesture),
    GameOption("cards", "Cards Sandbox", Icons.Filled.Style)
)

@Composable
fun GamesLobbyScreen(viewModel: ChatViewModel, navController: NavHostController) {
    val peers by viewModel.peers.collectAsState()
    
    var selectedGame by remember { mutableStateOf<GameOption?>(null) }
    var selectedMode by remember { mutableStateOf("single") } // single, multi
    var selectedPeerId by remember { mutableStateOf<String?>(null) }
    var selectedDifficulty by remember { mutableStateOf("medium") } // easy, medium, hard
    
    // Automatically switch to single-player if a newly selected game doesn't support multiplayer
    LaunchedEffect(selectedGame) {
        if (selectedGame?.id == "snake" || selectedGame?.id == "dino") {
            selectedMode = "single"
            selectedPeerId = null
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text("Games Hub", fontSize = 24.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))
        
        // 1. Select Game
        Text("SELECT GAME", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(AVAILABLE_GAMES) { game ->
                val isSelected = selectedGame?.id == game.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(16.dp)
                        )
                        .border(
                            2.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedGame = game }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(game.icon, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(game.name, fontSize = 18.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                AnimatedVisibility(visible = selectedGame != null) {
                    Column {
                        // Mode Selection
                        Text("MODE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ModeChip("Bot", selectedMode == "single", onClick = { selectedMode = "single"; selectedPeerId = null }, modifier = Modifier.defaultMinSize(minWidth = 100.dp))
                            if (selectedGame?.id != "snake" && selectedGame?.id != "dino") {
                                ModeChip("Pass & Play", selectedMode == "local_p2p", onClick = { selectedMode = "local_p2p"; selectedPeerId = null }, modifier = Modifier.defaultMinSize(minWidth = 120.dp))
                                ModeChip("Multiplayer", selectedMode == "multi", onClick = { selectedMode = "multi" }, modifier = Modifier.defaultMinSize(minWidth = 120.dp))
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Select Peer if Multiplayer
                        AnimatedVisibility(visible = selectedMode == "multi") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("SELECT OPPONENT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                if (peers.isEmpty()) {
                                    Text("No peers available. Go to Connect tab.", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                                } else {
                                    peers.values.forEach { peer ->
                                        val isPeerSelected = selectedPeerId == peer.nodeId
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                                .background(if (isPeerSelected) MaterialTheme.colorScheme.secondary.copy(alpha=0.2f) else MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                                .border(1.dp, if (isPeerSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f), RoundedCornerShape(12.dp))
                                                .clickable { selectedPeerId = peer.nodeId }
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(peer.avatar, fontSize = 24.sp)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(peer.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                        
                        // Difficulty Selection
                        AnimatedVisibility(visible = selectedMode == "single") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("DIFFICULTY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ModeChip("Easy", selectedDifficulty == "easy", onClick = { selectedDifficulty = "easy" }, modifier = Modifier.weight(1f))
                                    ModeChip("Med", selectedDifficulty == "medium", onClick = { selectedDifficulty = "medium" }, modifier = Modifier.weight(1f))
                                    ModeChip("Hard", selectedDifficulty == "hard", onClick = { selectedDifficulty = "hard" }, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = { 
                                val peer = if (selectedMode == "multi") selectedPeerId ?: "NONE" else "NONE"
                                navController.navigate("game_active/${selectedGame?.id ?: "dino"}/$selectedMode/$peer/$selectedDifficulty") 
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = (selectedMode == "single" || selectedMode == "local_p2p") || (selectedMode == "multi" && selectedPeerId != null)
                        ) {
                            Text("START GAME", fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(24.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text, 
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}
