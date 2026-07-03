package com.example.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.ChatViewModel

@Composable
fun ChessGameView(viewModel: ChatViewModel? = null, mode: String = "single", peerId: String = "NONE", isHost: Boolean = true, difficulty: String = "medium") {
    var selectedSquare by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var board by remember { mutableStateOf(initChessBoard()) }
    var chessSkin by remember { mutableStateOf("Classic") }
    
    var turn by remember { mutableStateOf('w') }
    val isMulti = mode == "multi"
    var myColor = if (!isMulti || isHost) 'w' else 'b'
    val gameStateFlow = viewModel?.gameStateFlow

    val hasWhiteKing = board.any { row -> row.any { it == "wK" } }
    val hasBlackKing = board.any { row -> row.any { it == "bK" } }
    val winner = when {
        !hasWhiteKing -> 'b'
        !hasBlackKing -> 'w'
        else -> null
    }
    
    LaunchedEffect(isMulti) {
        if (isMulti) {
            gameStateFlow?.collect { payload ->
                val parts = payload.split("|")
                if (parts[0] == "chess" && parts[1] == "move") {
                    val fromX = parts[2].toInt()
                    val fromY = parts[3].toInt()
                    val toX = parts[4].toInt()
                    val toY = parts[5].toInt()
                    val promoted = if (parts.size > 6) parts[6] else null
                    
                    val b = board.map { it.clone() }.toTypedArray()
                    if (b[fromY][fromX] != " ") {
                        b[toY][toX] = promoted ?: b[fromY][fromX]
                        b[fromY][fromX] = " "
                        board = b
                        turn = if (turn == 'w') 'b' else 'w'
                    }
                } else if (parts[0] == "chess" && parts[1] == "restart") {
                    board = initChessBoard()
                    turn = 'w'
                }
            }
        }
    }
    
    val isBot = mode == "single"
    LaunchedEffect(turn) {
        if (isBot && turn == 'b' && winner == null) {
            kotlinx.coroutines.delay(500) // slight delay for bot thinking
            val move = calculateBotMove(board, 'b', difficulty)
            if (move != null) {
                val (from, to) = move
                val b = board.map { it.clone() }.toTypedArray()
                var piece = b[from.second][from.first]
                if (piece == "bP" && to.second == 7) piece = "bQ"
                b[to.second][to.first] = piece
                b[from.second][from.first] = " "
                board = b
                turn = 'w'
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        androidx.compose.animation.AnimatedVisibility(visible = winner != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🏆",
                        fontSize = 48.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (winner == 'w') "WHITE WINS!" else "BLACK WINS!",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (winner == 'w') "The Black King has been captured!" else "The White King has been captured!",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            board = initChessBoard()
                            turn = 'w'
                            if (isMulti) viewModel?.sendGameState("chess|restart", peerId)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("RESET MATCH", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
            Text("Chess Sandbox", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) {
                    Text(chessSkin)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Classic") }, onClick = { chessSkin = "Classic"; expanded = false })
                    DropdownMenuItem(text = { Text("Outlined") }, onClick = { chessSkin = "Outlined"; expanded = false })
                    DropdownMenuItem(text = { Text("Neon") }, onClick = { chessSkin = "Neon"; expanded = false })
                    DropdownMenuItem(text = { Text("Wood") }, onClick = { chessSkin = "Wood"; expanded = false })
                }
            }
        }
        Text(if (winner != null) "Game Over" else if (turn == myColor && isMulti) "Your Turn ($myColor)" else if (isMulti) "Opponent's Turn" else "Turn: $turn", modifier = Modifier.padding(bottom = 8.dp))
        
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(16.dp).border(2.dp, Color.Black)) {
            Column(modifier = Modifier.fillMaxSize()) {
                for (rY in 0..7) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        for (rX in 0..7) {
                            val x = if (isHost || !isMulti) rX else 7 - rX
                            val y = if (isHost || !isMulti) rY else 7 - rY
                            val isSelected = selectedSquare?.first == x && selectedSquare?.second == y
                            val isDarkSquare = (x + y) % 2 != 0
                            val bg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha=0.5f) 
                                     else if (chessSkin == "Wood") { if (isDarkSquare) Color(0xFF8B5A2B) else Color(0xFFDEB887) }
                                     else if (chessSkin == "Neon") { if (isDarkSquare) Color(0xFF111122) else Color(0xFF222244) }
                                     else { if (!isDarkSquare) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background }
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(bg)
                                    .pointerInput(Unit) {
                                        detectTapGestures {
                                            if (winner != null) return@detectTapGestures
                                            if (selectedSquare == null) {
                                                if (board[y][x] != " ") {
                                                    if (!isMulti || board[y][x].first() == myColor) {
                                                        if (board[y][x].first() == turn) {
                                                            selectedSquare = Pair(x, y)
                                                        }
                                                    }
                                                }
                                            } else {
                                                val (fx, fy) = selectedSquare!!
                                                if (fx == x && fy == y) {
                                                    selectedSquare = null
                                                } else {
                                                    var piece = board[fy][fx]
                                                    val target = board[y][x]
                                                    if (target != " " && target.first() == piece.first()) {
                                                        if (!isMulti || piece.first() == myColor) {
                                                            selectedSquare = Pair(x, y)
                                                        }
                                                        return@detectTapGestures
                                                    }
                                                    if (!isValidChessMove(board, fx, fy, x, y)) {
                                                        return@detectTapGestures
                                                    }
                                                    
                                                    if (piece == "wP" && y == 0) piece = "wQ"
                                                    if (piece == "bP" && y == 7) piece = "bQ"

                                                    val b = board.map { it.clone() }.toTypedArray()
                                                    b[y][x] = piece
                                                    b[fy][fx] = " "
                                                    board = b
                                                    turn = if (turn == 'w') 'b' else 'w'
                                                    if (isMulti) viewModel?.sendGameState("chess|move|$fx|$fy|$x|$y|$piece", peerId)
                                                    selectedSquare = null
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val piece = board[y][x]
                                if (piece != " ") {
                                    val pieceColor = if (chessSkin == "Neon") {
                                        if (piece.first() == 'w') Color(0xFF00FFFF) else Color(0xFFFF00FF)
                                    } else {
                                        if (piece.first() == 'w') MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.primary
                                    }
                                    Text(
                                        text = getChessSymbol(piece, chessSkin),
                                        fontSize = 32.sp,
                                        color = pieceColor,
                                        style = if (chessSkin == "Neon") androidx.compose.ui.text.TextStyle(shadow = androidx.compose.ui.graphics.Shadow(color = pieceColor, blurRadius = 8f)) else androidx.compose.ui.text.TextStyle.Default
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        if (winner == null) {
            Button(
                onClick = {
                    board = initChessBoard()
                    turn = 'w'
                    if (isMulti) viewModel?.sendGameState("chess|restart", peerId)
                },
                colors = ButtonDefaults.filledTonalButtonColors(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("RESET MATCH")
            }
            Spacer(Modifier.height(8.dp))
            Text("Move pieces freely. Play fair!", color = Color.Gray)
        }
    }
}

fun initChessBoard(): Array<Array<String>> {
    return arrayOf(
        arrayOf("bR", "bN", "bB", "bQ", "bK", "bB", "bN", "bR"),
        arrayOf("bP", "bP", "bP", "bP", "bP", "bP", "bP", "bP"),
        Array(8) { " " },
        Array(8) { " " },
        Array(8) { " " },
        Array(8) { " " },
        arrayOf("wP", "wP", "wP", "wP", "wP", "wP", "wP", "wP"),
        arrayOf("wR", "wN", "wB", "wQ", "wK", "wB", "wN", "wR")
    )
}

fun getChessSymbol(piece: String, skin: String = "Classic"): String {
    val isWhite = piece.first() == 'w'
    return if (skin == "Outlined") {
        if (isWhite) {
            when (piece.substring(1)) {
                "K" -> "♔"; "Q" -> "♕"; "R" -> "♖"; "B" -> "♗"; "N" -> "♘"; "P" -> "♙"
                else -> ""
            }
        } else {
            when (piece.substring(1)) {
                "K" -> "♚"; "Q" -> "♛"; "R" -> "♜"; "B" -> "♝"; "N" -> "♞"; "P" -> "♟"
                else -> ""
            }
        }
    } else {
        when (piece.substring(1)) {
            "K" -> "♚"; "Q" -> "♛"; "R" -> "♜"; "B" -> "♝"; "N" -> "♞"; "P" -> "♟"
            else -> ""
        }
    }
}

fun isValidChessMove(board: Array<Array<String>>, fx: Int, fy: Int, tx: Int, ty: Int): Boolean {
    val piece = board[fy][fx]
    val target = board[ty][tx]
    if (piece == " ") return false
    val color = piece[0]
    val type = piece[1]
    
    // Cannot capture own
    if (target != " " && target[0] == color) return false

    val dx = tx - fx
    val dy = ty - fy
    val adx = kotlin.math.abs(dx)
    val ady = kotlin.math.abs(dy)

    fun isPathClear(): Boolean {
        val stepX = if (dx > 0) 1 else if (dx < 0) -1 else 0
        val stepY = if (dy > 0) 1 else if (dy < 0) -1 else 0
        var cx = fx + stepX
        var cy = fy + stepY
        while (cx != tx || cy != ty) {
            if (board[cy][cx] != " ") return false
            cx += stepX
            cy += stepY
        }
        return true
    }

    when (type) {
        'P' -> {
            val fwd = if (color == 'w') -1 else 1
            val startY = if (color == 'w') 6 else 1
            if (dx == 0) {
                if (dy == fwd && target == " ") return true
                if (dy == fwd * 2 && fy == startY && target == " " && board[fy + fwd][fx] == " ") return true
            } else if (adx == 1 && dy == fwd && target != " ") {
                return true // diagonal capture
            }
            return false
        }
        'R' -> return (dx == 0 || dy == 0) && isPathClear()
        'N' -> return (adx == 1 && ady == 2) || (adx == 2 && ady == 1)
        'B' -> return adx == ady && isPathClear()
        'Q' -> return (dx == 0 || dy == 0 || adx == ady) && isPathClear()
        'K' -> return adx <= 1 && ady <= 1
    }
    return false
}
