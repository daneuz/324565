package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.core.animateOffsetAsState

@Composable
fun VirtualJoystick(
    modifier: Modifier = Modifier,
    radius: Float = 100f,
    thumbRadius: Float = 40f,
    onPositionChanged: (Offset) -> Unit, // returns normalized offset -1 to 1
    botOffset: Offset? = null // if this is controlled by bot
) {
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    
    // If botOffset is provided, use it for visual representation
    val targetPosition = botOffset ?: dragPosition
    val animatedPosition by animateOffsetAsState(targetValue = targetPosition, label = "joystick")

    Box(
        modifier = modifier
            .size((radius * 2).dp)
            .pointerInput(Unit) {
                if (botOffset == null) {
                    detectDragGestures(
                        onDragStart = {},
                        onDragEnd = {
                            dragPosition = Offset.Zero
                            onPositionChanged(Offset.Zero)
                        },
                        onDragCancel = {
                            dragPosition = Offset.Zero
                            onPositionChanged(Offset.Zero)
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        var newPos = dragPosition + dragAmount
                        val distance = kotlin.math.sqrt(newPos.x * newPos.x + newPos.y * newPos.y)
                        
                        if (distance > radius) {
                            val ratio = radius / distance
                            newPos = Offset(newPos.x * ratio, newPos.y * ratio)
                        }
                        dragPosition = newPos
                        
                        val normalized = Offset(dragPosition.x / radius, dragPosition.y / radius)
                        onPositionChanged(normalized)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            
            // Base Draw
            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = radius,
                center = center
            )
            
            // Thumb Draw
            drawCircle(
                color = if (botOffset != null) Color.Cyan.copy(alpha = 0.8f) else Color.Magenta.copy(alpha = 0.8f),
                radius = thumbRadius,
                center = center + animatedPosition
            )
        }
    }
}

@Composable
fun PingPongGameView(viewModel: com.example.viewmodel.ChatViewModel? = null, mode: String = "single", peerId: String = "NONE", isHost: Boolean = true, isPaused: Boolean = false, difficulty: String = "medium") {
    var leftPaddleY by remember { mutableStateOf(0.5f) }
    var rightPaddleY by remember { mutableStateOf(0.5f) }
    
    var joystickLeftY by remember { mutableStateOf(0f) }
    var joystickRightY by remember { mutableStateOf(0f) }
    var botJoystickY by remember { mutableStateOf(0f) }
    
    var ballRatio by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var ballVelRatio by remember { mutableStateOf(Offset(0.01f, 0.01f)) }
    
    var isPlaying by remember { mutableStateOf(false) }
    var scoreLeft by remember { mutableStateOf(0) }
    var scoreRight by remember { mutableStateOf(0) }
    
    val isMulti: Boolean = mode == "multi"
    val isBot: Boolean = mode == "single"
    
    val paddleHeightRatio = 0.2f
    val paddleWidth = 20f
    
    val gameStateFlow = viewModel?.gameStateFlow

    // Send game state (paddle y) to peer
    LaunchedEffect(isMulti) {
        if (isMulti) {
            var lastSent: Float = -1f
            while (true) {
                val current = if (isHost) leftPaddleY else rightPaddleY
                if (kotlin.math.abs(current - lastSent) > 0.005f) {
                    val side = if (isHost) "left" else "right"
                    viewModel?.sendGameState("pingpong|paddle|$side|$current", peerId)
                    lastSent = current
                }
                delay(16)
            }
        }
    }
    
    // Listen to network state
    LaunchedEffect(isMulti) {
        if (isMulti) {
            gameStateFlow?.collect { payload ->
                val parts = payload.split("|")
                if (parts[0] == "pingpong") {
                    when (parts[1]) {
                        "paddle" -> {
                            val side = parts[2]
                            val y = parts[3].toFloatOrNull() ?: 0.5f
                            if (side == "left") leftPaddleY = y else rightPaddleY = y
                        }
                        "ball", "ballbounce" -> {
                            val bx = parts[2].toFloat()
                            val by = parts[3].toFloat()
                            val bvx = parts.getOrNull(4)?.toFloat() ?: ballVelRatio.x
                            val bvy = parts.getOrNull(5)?.toFloat() ?: ballVelRatio.y
                            if (parts[1] == "ballbounce" || !isHost) {
                                ballRatio = Offset(bx, by)
                                ballVelRatio = Offset(bvx, bvy)
                            }
                        }
                        "score" -> {
                            scoreLeft = parts[2].toInt()
                            scoreRight = parts[3].toInt()
                        }
                        "start" -> if (!isHost) isPlaying = true
                        "stop" -> if (!isHost) isPlaying = false
                    }
                }
            }
        }
    }
    
    LaunchedEffect(isPlaying, isPaused) {
        if (isPlaying && !isPaused) {
            var lastTime = 0L
            var lastNetSend = 0L
            while (isPlaying) {
                androidx.compose.runtime.withFrameMillis { time ->
                    if (lastTime == 0L) lastTime = time
                    val dt = (time - lastTime) / 16f
                    lastTime = time

                    var newBx = ballRatio.x + ballVelRatio.x * dt
                    var newBy = ballRatio.y + ballVelRatio.y * dt
                    
                    // Paddle velocities from joysticks
                    val paddleSpeed = 0.035f * dt
                    if (isHost || !isMulti) {
                        leftPaddleY += joystickLeftY * paddleSpeed
                        leftPaddleY = leftPaddleY.coerceIn(paddleHeightRatio/2f, 1f - paddleHeightRatio/2f)
                    }
                    if (!isHost || (!isMulti && !isBot)) {
                        rightPaddleY += joystickRightY * paddleSpeed
                        rightPaddleY = rightPaddleY.coerceIn(paddleHeightRatio/2f, 1f - paddleHeightRatio/2f)
                    }
                    if (isBot && isHost) {
                        val botMaxSpeed = when(difficulty) {
                            "hard" -> 0.045f
                            "medium" -> 0.025f
                            else -> 0.015f
                        } * dt
                        val errorMargin = when(difficulty) {
                            "hard" -> 0.02f
                            "medium" -> 0.08f
                            else -> 0.15f
                        }
                        
                        // Overwrite botJoystickY based on simulation internally rather than just keeping it to 1f/-1f
                        if (ballVelRatio.x > 0) { // Incoming ball
                            var simY = newBy
                            var tempVy = ballVelRatio.y
                            val stepsX = (0.95f - newBx) / ballVelRatio.x
                            simY += tempVy * stepsX
                            
                            while (simY < 0f || simY > 1f) {
                                if (simY < 0f) simY = -simY
                                if (simY > 1f) simY = 2f - simY
                            }
                            
                            if (rightPaddleY < simY - errorMargin) rightPaddleY += botMaxSpeed
                            else if (rightPaddleY > simY + errorMargin) rightPaddleY -= botMaxSpeed
                        } else { 
                            if (rightPaddleY < 0.48f) rightPaddleY += botMaxSpeed * 0.5f
                            else if (rightPaddleY > 0.52f) rightPaddleY -= botMaxSpeed * 0.5f
                        }
                        rightPaddleY = rightPaddleY.coerceIn(paddleHeightRatio/2f, 1f - paddleHeightRatio/2f)
                    }
                    
                    // Bounds
                    if (newBy <= 0f) { newBy = 0f; ballVelRatio = ballVelRatio.copy(y = kotlin.math.abs(ballVelRatio.y)) }
                    if (newBy >= 1f) { newBy = 1f; ballVelRatio = ballVelRatio.copy(y = -kotlin.math.abs(ballVelRatio.y)) }
                    
                    // Paddle collisions (Both simulate)
                    if (newBx <= 0.05f && ballVelRatio.x < 0 && newBy in (leftPaddleY - paddleHeightRatio/2f)..(leftPaddleY + paddleHeightRatio/2f)) {
                        val norm = (newBy - leftPaddleY) / (paddleHeightRatio / 2f)
                        val angle = norm * (kotlin.math.PI / 3) 
                        val speed = kotlin.math.min(0.04f, kotlin.math.sqrt(ballVelRatio.x * ballVelRatio.x + ballVelRatio.y * ballVelRatio.y) * 1.05f)
                        ballVelRatio = Offset(
                            (kotlin.math.cos(angle) * speed).toFloat(),
                            (kotlin.math.sin(angle) * speed).toFloat()
                        )
                        newBx = 0.06f
                        if (isMulti && isHost) viewModel?.sendGameState("pingpong|ballbounce|$newBx|$newBy|${ballVelRatio.x}|${ballVelRatio.y}", peerId)
                    }
                    
                    if (newBx >= 0.95f && ballVelRatio.x > 0 && newBy in (rightPaddleY - paddleHeightRatio/2f)..(rightPaddleY + paddleHeightRatio/2f)) {
                        val norm = (newBy - rightPaddleY) / (paddleHeightRatio / 2f)
                        val angle = norm * (kotlin.math.PI / 3)
                        val speed = kotlin.math.min(0.04f, kotlin.math.sqrt(ballVelRatio.x * ballVelRatio.x + ballVelRatio.y * ballVelRatio.y) * 1.05f)
                        ballVelRatio = Offset(
                            -(kotlin.math.cos(angle) * speed).toFloat(),
                            (kotlin.math.sin(angle) * speed).toFloat()
                        )
                        newBx = 0.94f
                        if (isMulti && !isHost) viewModel?.sendGameState("pingpong|ballbounce|$newBx|$newBy|${ballVelRatio.x}|${ballVelRatio.y}", peerId)
                    }
                    
                    // Host manages scores
                    if (isHost || !isMulti) {
                        if (newBx < 0f) {
                            scoreRight++; isPlaying = false; ballRatio = Offset(0.5f, 0.5f)
                            if (isMulti) {
                                viewModel?.sendGameState("pingpong|score|$scoreLeft|$scoreRight", peerId)
                                viewModel?.sendGameState("pingpong|stop", peerId)
                            }
                        } else if (newBx > 1f) {
                            scoreLeft++; isPlaying = false; ballRatio = Offset(0.5f, 0.5f)
                            if (isMulti) {
                                viewModel?.sendGameState("pingpong|score|$scoreLeft|$scoreRight", peerId)
                                viewModel?.sendGameState("pingpong|stop", peerId)
                            }
                        }
                    } else if (isMulti && !isHost) {
                        if (newBx < -0.1f || newBx > 1.1f) {
                            isPlaying = false; ballRatio = Offset(0.5f, 0.5f)
                        }
                    }
                    
                    if (isPlaying) {
                        ballRatio = Offset(newBx, newBy)
                        
                        if (isMulti && isHost && time - lastNetSend > 20) {
                            lastNetSend = time
                            viewModel?.sendGameState("pingpong|ball|${ballRatio.x}|${ballRatio.y}|${ballVelRatio.x}|${ballVelRatio.y}", peerId)
                        }
                    }
                }
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (isHost || !isMulti) {
                    VirtualJoystick(
                        modifier = Modifier.padding(bottom = 32.dp),
                        onPositionChanged = { joystickLeftY = it.y }
                    )
                }
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (!isHost || (!isMulti && !isBot)) {
                    VirtualJoystick(
                        modifier = Modifier.padding(bottom = 32.dp),
                        onPositionChanged = { joystickRightY = it.y }
                    )
                } else if (isBot && isHost) {
                    VirtualJoystick(
                        modifier = Modifier.padding(bottom = 32.dp),
                        onPositionChanged = { },
                        botOffset = Offset(0f, botJoystickY * 60f) // 60f is thumb limit inside 100f radius minus 40f thumb scale
                    )
                }
            }
        }

        if (!isPlaying && (!isMulti || isHost)) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isMulti) {
                    val modeText = if (isBot) "Playing against Bot ($difficulty)" else "Pass & Play"
                    Text(modeText, color = androidx.compose.ui.graphics.Color.White, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                androidx.compose.material3.Button(onClick = { 
                    isPlaying = true; ballVelRatio = Offset(0.015f, if (Math.random() > 0.5) 0.015f else -0.015f)
                    if (isMulti && isHost) viewModel?.sendGameState("pingpong|start", peerId)
                }) {
                    Text("Tap to Start")
                }
            }
        }
        
        Text("Host: $scoreLeft", modifier = Modifier.align(Alignment.TopCenter).padding(16.dp), color = androidx.compose.ui.graphics.Color.White)
        Text("Client: $scoreRight", modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp), color = androidx.compose.ui.graphics.Color.White)
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            val w = size.width
            val ph = h * paddleHeightRatio
            
            // Center line
            drawLine(
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f),
                Offset(w/2, 0f),
                Offset(w/2, h),
                4f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
            )
            
            // Left Paddle
            val corner = androidx.compose.ui.geometry.CornerRadius(15f, 15f)
            drawRoundRect(androidx.compose.ui.graphics.Color.Magenta, Offset(10f, (leftPaddleY * h) - ph/2f), Size(paddleWidth, ph), cornerRadius = corner)
            
            // Right Paddle
            drawRoundRect(androidx.compose.ui.graphics.Color.Cyan, Offset(w - paddleWidth - 10f, (rightPaddleY * h) - ph/2f), Size(paddleWidth, ph), cornerRadius = corner)
            
            // Ball
            val renderBallX = ballRatio.x * w
            val renderBallY = ballRatio.y * h
            drawCircle(androidx.compose.ui.graphics.Color.White, 15f, Offset(renderBallX, renderBallY))
            drawCircle(androidx.compose.ui.graphics.Color.Cyan.copy(alpha=0.4f), 25f, Offset(renderBallX, renderBallY))
        }
    }
}

@Composable
fun SnakeGameView(viewModel: com.example.viewmodel.ChatViewModel? = null, mode: String = "single", peerId: String = "NONE", isHost: Boolean = true, isPaused: Boolean = false, difficulty: String = "medium") {
    val gridSize = 20
    var snake by remember { mutableStateOf(listOf(Offset(10f, 10f))) }
    var prevSnake by remember { mutableStateOf(listOf(Offset(10f, 10f))) }
    var interpProgress by remember { mutableStateOf(1f) }
    var direction by remember { mutableStateOf(Offset(1f, 0f)) }
    var lastMappedDirection by remember { mutableStateOf(Offset(1f, 0f)) }
    var apple by remember { mutableStateOf(Offset(15f, 15f)) }
    
    var goldenApple by remember { mutableStateOf<Offset?>(null) }
    var goldenAppleTimer by remember { mutableStateOf(0) }
    
    var isPlaying by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var remoteScore by remember { mutableStateOf(0) }
    var remoteDead by remember { mutableStateOf(false) }
    var isDead by remember { mutableStateOf(false) }
    var snakeSkin by remember { mutableStateOf("Classic") }
    
    val isMulti = mode == "multi"
    val gameStateFlow = viewModel?.gameStateFlow

    LaunchedEffect(score, isDead) {
        if (isMulti) {
            viewModel?.sendGameState("snake|state|$score|${if (isDead) 1 else 0}", peerId)
        }
    }
    
    LaunchedEffect(isMulti) {
        if (isMulti) {
            gameStateFlow?.collect { payload ->
                val parts = payload.split("|")
                if (parts[0] == "snake" && parts[1] == "state") {
                    remoteScore = parts[2].toIntOrNull() ?: remoteScore
                    remoteDead = parts[3] == "1"
                }
            }
        }
    }
    
    LaunchedEffect(isPlaying, isPaused) {
        if (isPlaying && !isPaused) {
            var lastTime = 0L
            while (isPlaying) {
                androidx.compose.runtime.withFrameMillis { time ->
                    if (lastTime == 0L) lastTime = time
                    val delayMs = kotlin.math.max(50L, 120L - (score * 2L))
                    val elapsed = time - lastTime
                    if (elapsed >= delayMs) {
                        lastTime = time
                        interpProgress = 0f
                        lastMappedDirection = direction
                        val head = snake.first()
                        var nextHead = head + direction
                        
                        // Wrap around
                        if (nextHead.x < 0) nextHead = nextHead.copy(x = gridSize - 1f)
                        if (nextHead.x >= gridSize) nextHead = nextHead.copy(x = 0f)
                        if (nextHead.y < 0) nextHead = nextHead.copy(y = gridSize - 1f)
                        if (nextHead.y >= gridSize) nextHead = nextHead.copy(y = 0f)
                        
                        // Collision with self
                        if (snake.contains(nextHead)) {
                            isPlaying = false
                            isDead = true
                            interpProgress = 1f
                        } else {
                            val newSnake = snake.toMutableList()
                            newSnake.add(0, nextHead)
                            
                            if (nextHead == apple) {
                                score++
                                apple = Offset((0 until gridSize).random().toFloat(), (0 until gridSize).random().toFloat())
                            } else if (goldenApple != null && nextHead == goldenApple) {
                                score += 5
                                goldenApple = null
                            } else {
                                newSnake.removeLast()
                            }
                            prevSnake = snake
                            snake = newSnake
                            
                            // Golden apple logic
                            if (goldenApple == null && java.lang.Math.random() < 0.05) {
                                goldenApple = Offset((0 until gridSize).random().toFloat(), (0 until gridSize).random().toFloat())
                                goldenAppleTimer = 30 // ticks
                            } else if (goldenApple != null) {
                                goldenAppleTimer--
                                if (goldenAppleTimer <= 0) goldenApple = null
                            }
                        }
                    } else {
                        interpProgress = elapsed.toFloat() / delayMs.toFloat()
                    }
                }
            }
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("You: $score${if(isDead) " (DEAD)" else ""}", color = if (isDead) Color.Red else Color.Unspecified)
            if (isMulti) {
                Text("Peer: $remoteScore${if(remoteDead) " (DEAD)" else ""}", color = if (remoteDead) Color.Red else Color.Unspecified)
            }
        }
        
        if (!isPlaying) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isDead) { // Fresh game
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Snake Skin", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                            Button(onClick = {
                                val skins = listOf("Classic", "Neon", "Cyberpunk", "Theme")
                                snakeSkin = skins[(skins.indexOf(snakeSkin) + 1) % skins.size]
                            }) { Text(snakeSkin) }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
                Button(onClick = { 
                    isPlaying = true
                    isDead = false
                    snake = listOf(Offset(10f, 10f))
                    prevSnake = listOf(Offset(10f, 10f))
                    interpProgress = 1f
                    direction = Offset(1f, 0f)
                    lastMappedDirection = direction
                    score = 0
                }) {
                    Text(if (isDead) "Restart Game" else "Tap Screen to Play")
                }
            }
        }
        
        val tPrimary = MaterialTheme.colorScheme.primary
        val tTertiary = MaterialTheme.colorScheme.tertiary
        val tOnBg = MaterialTheme.colorScheme.onBackground
        val tBg = MaterialTheme.colorScheme.background
        
        Canvas(modifier = Modifier.fillMaxSize().padding(top = 100.dp)) {
            val cellW = size.width / gridSize
            val cellH = size.height / gridSize
            
            // Draw Grid lines faintly
            for (i in 0..gridSize) {
                drawLine(Color.White.copy(alpha=0.05f), Offset(i*cellW, 0f), Offset(i*cellW, size.height))
                drawLine(Color.White.copy(alpha=0.05f), Offset(0f, i*cellH), Offset(size.width, i*cellH))
            }
            
            // Draw Apple
            val cx = apple.x * cellW + cellW / 2f
            val cy = apple.y * cellH + cellH / 2f
            drawCircle(Color.Red, (cellW / 2f) * 0.8f, Offset(cx, cy))
            // Leaf
            drawCircle(Color.Green, (cellW / 2f) * 0.3f, Offset(cx + cellW/6f, cy - cellH/3f))
            
            // Draw Golden Apple
            goldenApple?.let { ga ->
                val gcx = ga.x * cellW + cellW / 2f
                val gcy = ga.y * cellH + cellH / 2f
                val pulse = (kotlin.math.sin(System.currentTimeMillis() / 200.0).toFloat() + 1f) / 2f
                val glowAlpha = 0.5f + (pulse * 0.5f)
                drawCircle(Color.Yellow.copy(alpha = glowAlpha), cellW, Offset(gcx, gcy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.White, Color(0xFFFFD700), Color(0xFFDAA520)), center = Offset(gcx - cellW/4f, gcy - cellH/4f), radius = cellW), (cellW / 2f) * 0.8f, Offset(gcx, gcy))
                // Sparkles
                if (pulse > 0.5f) {
                    drawCircle(Color.White, 2f, Offset(gcx + cellW/3f, gcy - cellH/3f))
                    drawCircle(Color.White, 3f, Offset(gcx - cellW/2f, gcy + cellH/4f))
                }
            }
            
            // Draw Snake
            val safeSnakeSize = kotlin.math.max(1f, (snake.size - 1).toFloat())
            snake.forEachIndexed { index, segment ->
                val prevSegmentRaw = if (index < prevSnake.size) prevSnake[index] else prevSnake.lastOrNull() ?: segment
                // Account for wrap-around visually (don't interpolate smoothly across the entire screen)
                val isWrappedX = kotlin.math.abs(segment.x - prevSegmentRaw.x) > 1f
                val isWrappedY = kotlin.math.abs(segment.y - prevSegmentRaw.y) > 1f
                
                val prevSegment = Offset(
                    if (isWrappedX) segment.x else prevSegmentRaw.x,
                    if (isWrappedY) segment.y else prevSegmentRaw.y
                )
                
                // Calculate interp coords
                val ix = prevSegment.x + (segment.x - prevSegment.x) * interpProgress
                val iy = prevSegment.y + (segment.y - prevSegment.y) * interpProgress
                
                val px = ix * cellW
                val py = iy * cellH
                val t = index.toFloat() / safeSnakeSize
                
                when (snakeSkin) {
                    "Neon" -> {
                        val baseColor = if(isDead) Color.DarkGray else androidx.compose.ui.graphics.lerp(Color.Cyan, Color(0xFFFF00FF), t)
                        drawRoundRect(
                            color = baseColor, 
                            topLeft = Offset(px + 2f, py + 2f), 
                            size = Size(cellW - 4f, cellH - 4f), 
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellW/4f, cellH/4f)
                        )
                        // Glow
                        if (!isDead) drawRoundRect(baseColor.copy(alpha=0.3f), topLeft = Offset(px - 2f, py - 2f), size = Size(cellW + 4f, cellH + 4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellW/2f, cellH/2f))
                        
                        if (index == 0 && !isDead) {
                            val eyeSize = cellW * 0.15f
                            drawCircle(Color.White, eyeSize, Offset(px + cellW/3f, py + cellH/3f))
                            drawCircle(Color.White, eyeSize, Offset(px + cellW*2f/3f, py + cellH/3f))
                        }
                    }
                    "Cyberpunk" -> {
                        val baseColor = if(isDead) Color.LightGray else Color.Yellow
                        drawRect(
                            color = baseColor, 
                            topLeft = Offset(px + 1f, py + 1f), 
                            size = Size(cellW - 2f, cellH - 2f)
                        )
                        drawRect(if(isDead) Color.Gray else Color.Black, Offset(px + cellW/4f, py + cellH/4f), Size(cellW/2f, cellH/2f))
                        
                        if (index == 0 && !isDead) { // Cyber eyes
                            drawRect(Color.Red, Offset(px + cellW/5f, py + cellH/4f), Size(cellW/4f, cellH/6f))
                            drawRect(Color.Red, Offset(px + cellW*3f/5f, py + cellH/4f), Size(cellW/4f, cellH/6f))
                        }
                    }
                    "Theme" -> {
                        val baseColor = if (isDead) Color.LightGray else androidx.compose.ui.graphics.lerp(tPrimary, tTertiary, t)
                        drawRoundRect(
                            color = baseColor, 
                            topLeft = Offset(px + 2f, py + 2f), 
                            size = Size(cellW - 4f, cellH - 4f), 
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellW/4f, cellH/4f)
                        )
                        
                        if (index == 0 && !isDead) { // Eyes
                            val eyeSize = cellW * 0.18f
                            drawCircle(tOnBg, eyeSize, Offset(px + cellW/3f, py + cellH/3f))
                            drawCircle(tOnBg, eyeSize, Offset(px + cellW*2f/3f, py + cellH/3f))
                            drawCircle(tBg, eyeSize * 0.5f, Offset(px + cellW/3f + direction.x * 2f, py + cellH/3f + direction.y * 2f))
                            drawCircle(tBg, eyeSize * 0.5f, Offset(px + cellW*2f/3f + direction.x * 2f, py + cellH/3f + direction.y * 2f))
                        }
                    }
                    else -> { // Classic
                        val baseColor = if (isDead) Color.LightGray else androidx.compose.ui.graphics.lerp(Color(0xFF00FF88), Color(0xFF00BFFF), t)
                        drawRoundRect(
                            color = baseColor, 
                            topLeft = Offset(px + 2f, py + 2f), 
                            size = Size(cellW - 4f, cellH - 4f), 
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellW/4f, cellH/4f)
                        )
                        
                        if (index == 0 && !isDead) { // Eyes
                            val eyeSize = cellW * 0.18f
                            drawCircle(Color.White, eyeSize, Offset(px + cellW/3f, py + cellH/3f))
                            drawCircle(Color.White, eyeSize, Offset(px + cellW*2f/3f, py + cellH/3f))
                            drawCircle(Color.Black, eyeSize * 0.5f, Offset(px + cellW/3f + direction.x * 2f, py + cellH/3f + direction.y * 2f))
                            drawCircle(Color.Black, eyeSize * 0.5f, Offset(px + cellW*2f/3f + direction.x * 2f, py + cellH/3f + direction.y * 2f))
                        }
                    }
                }
            }
        }
        VirtualJoystick(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            onPositionChanged = { offset ->
                if (offset.x == 0f && offset.y == 0f) return@VirtualJoystick
                if (kotlin.math.abs(offset.x) > kotlin.math.abs(offset.y)) {
                    if (offset.x > 0.3f && lastMappedDirection.x == 0f) direction = Offset(1f, 0f)
                    else if (offset.x < -0.3f && lastMappedDirection.x == 0f) direction = Offset(-1f, 0f)
                } else {
                    if (offset.y > 0.3f && lastMappedDirection.y == 0f) direction = Offset(0f, 1f)
                    else if (offset.y < -0.3f && lastMappedDirection.y == 0f) direction = Offset(0f, -1f)
                }
            }
        )
    }
}
@Composable
fun DinoGameView(viewModel: com.example.viewmodel.ChatViewModel? = null, mode: String = "single", peerId: String = "NONE", isHost: Boolean = true, isPaused: Boolean = false, difficulty: String = "medium") {
    var dinoY by remember { mutableStateOf(0f) }
    var dinoVelocity by remember { mutableStateOf(0f) }
    var obsX by remember { mutableStateOf(1000f) } // normalized or just let it scale
    var obsType by remember { mutableStateOf(0) } // 0 = cactus, 1 = pterodactyl
    var isPlaying by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var remoteScore by remember { mutableStateOf(0) }
    var isDead by remember { mutableStateOf(false) }
    var remoteDead by remember { mutableStateOf(false) }
    var dinoSkin by remember { mutableStateOf("Chameleon") }
    var obsSkin by remember { mutableStateOf("Classic") }
    
    val gravity = 1.5f
    val jumpStrength = -25f
    val groundY = 0f
    
    val isMulti = mode == "multi"
    val gameStateFlow = viewModel?.gameStateFlow

    LaunchedEffect(score, isDead) {
        if (isMulti) {
            viewModel?.sendGameState("dino|state|$score|${if (isDead) 1 else 0}", peerId)
        }
    }
    
    LaunchedEffect(isMulti) {
        if (isMulti) {
            gameStateFlow?.collect { payload ->
                val parts = payload.split("|")
                if (parts[0] == "dino" && parts[1] == "state") {
                    remoteScore = parts[2].toIntOrNull() ?: remoteScore
                    remoteDead = parts[3] == "1"
                }
            }
        }
    }
    
    LaunchedEffect(isPlaying, isPaused) {
        if (isPlaying && !isPaused) {
            var lastTime = 0L
            while (isPlaying) {
                androidx.compose.runtime.withFrameMillis { time ->
                    if (lastTime == 0L) lastTime = time
                    val dt = (time - lastTime) / 16f
                    lastTime = time

                    // Gravity logic
                    dinoVelocity += gravity * dt
                    dinoY += dinoVelocity * dt
                    
                    if (dinoY >= groundY) {
                        dinoY = groundY
                        dinoVelocity = 0f
                    }
                    
                    // Obstacle logic
                    obsX -= 15f * dt
                    if (obsX < -50f) {
                        obsX = 1000f
                        obsType = if (java.lang.Math.random() > 0.7) 1 else 0
                        score++
                    }
                    
                    // Collision logic
                    val dinoHitbox = androidx.compose.ui.geometry.Rect(50f, dinoY, 90f, dinoY + 50f)
                    val obsHitbox = if (obsType == 0) {
                        androidx.compose.ui.geometry.Rect(obsX - 10f, 10f, obsX + 30f, 60f) // relative to actualGround
                    } else {
                        androidx.compose.ui.geometry.Rect(obsX, -80f, obsX + 40f, -40f) // flying
                    }
                    
                    if (obsX in 10f..100f) {
                        if (obsType == 0 && dinoY > -60f) {
                            isPlaying = false
                            isDead = true
                        } else if (obsType == 1 && dinoY in -90f..-30f) {
                            isPlaying = false
                            isDead = true
                        }
                    }
                }
            }
        }
    }
    
    val gameTimeMs = System.currentTimeMillis()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    if (!isPlaying && !isDead) { // if dead, can't restart until reset maybe? or auto restart
                        isPlaying = true
                        obsX = size.width.toFloat()
                        score = 0
                        isDead = false
                    } else if (isDead) {
                        isDead = false
                        isPlaying = true
                        obsX = size.width.toFloat()
                        score = 0
                    } else if (dinoY == groundY) { // Jump only if grounded
                        dinoVelocity = jumpStrength
                    }
                }
            }
    ) {
        Row(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("You: $score${if(isDead) " (DEAD)" else ""}", color = if (isDead) Color.Red else Color.Unspecified)
            if (isMulti) {
                Text("Peer: $remoteScore${if(remoteDead) " (DEAD)" else ""}", color = if (remoteDead) Color.Red else Color.Unspecified)
            }
        }
        
        if (!isPlaying) {
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isDead) { // Only show when starting a fresh game
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Dino Skin", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                            Button(onClick = {
                                val skins = listOf("Chameleon", "Blocky", "Ghost", "Theme")
                                dinoSkin = skins[(skins.indexOf(dinoSkin) + 1) % skins.size]
                            }) { Text(dinoSkin) }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Obstacle Skin", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                            Button(onClick = {
                                val skins = listOf("Classic", "Neon", "City", "Theme")
                                obsSkin = skins[(skins.indexOf(obsSkin) + 1) % skins.size]
                            }) { Text(obsSkin) }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
                Button(onClick = { 
                    isPlaying = true
                    obsX = 1000f
                    score = 0
                    isDead = false
                }) {
                    Text(if (isDead) "Restart Game" else "Start Game (Tap Canvas to Jump)")
                }
            }
        }
        
        val tPrimary = MaterialTheme.colorScheme.primary
        val tTertiary = MaterialTheme.colorScheme.tertiary
        val tOnBg = MaterialTheme.colorScheme.onBackground
        val tBg = MaterialTheme.colorScheme.background
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val actualGround = height * 0.8f
            
            // Sunset gradient
            drawRect(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(Color(0xFFFF9800).copy(alpha=0.3f), Color.Transparent),
                    startY = 0f, endY = actualGround
                )
            )
            
            // Ground
            drawLine(Color(0xFF8B4513), Offset(0f, actualGround), Offset(width, actualGround), 10f)
            
            val hue = (gameTimeMs / 20f) % 360f
            val dx = 50f
            val dy = actualGround - 50f + dinoY
            
            // Dino Skins
            when (dinoSkin) {
                "Blocky" -> {
                    val bodyColor = if(isDead) Color.Gray else Color(0xFF8BC34A)
                    drawRect(bodyColor, Offset(dx, dy), Size(40f, 40f)) // Body
                    drawRect(if(isDead) Color.DarkGray else Color(0xFF689F38), Offset(dx + 25f, dy - 15f), Size(20f, 20f)) // Head
                    drawRect(Color.White, Offset(dx + 35f, dy - 10f), Size(5f,5f)) // Eye
                    val leg1 = if (isPlaying && (gameTimeMs / 100) % 2 == 0L) 30f else 40f
                    val leg2 = if (isPlaying && (gameTimeMs / 100) % 2 != 0L) 30f else 40f
                    drawRect(bodyColor, Offset(dx + 5f, dy + leg1), Size(10f, 50f - leg1))
                    drawRect(bodyColor, Offset(dx + 25f, dy + leg2), Size(10f, 50f - leg2))
                }
                "Ghost" -> {
                    val bodyColor = Color(0xAAFFFFFF)
                    drawRoundRect(bodyColor, Offset(dx, dy), Size(50f, 40f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f))
                    drawCircle(bodyColor, 18f, Offset(dx + 50f, dy + 10f))
                    drawCircle(Color.Red.copy(alpha=0.5f), 4f, Offset(dx + 55f, dy + 5f))
                    // Wavy tail
                    val wave = kotlin.math.sin(gameTimeMs / 150.0).toFloat() * 5f
                    drawRoundRect(bodyColor, Offset(dx - 15f, dy + 15f + wave), Size(20f, 10f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f,5f))
                }
                "Theme" -> {
                    val bodyColor = tPrimary
                    val bellyColor = tTertiary
                    val bodyStyle = androidx.compose.ui.graphics.drawscope.Fill
                    
                    // Body
                    drawRoundRect(bodyColor, Offset(dx, dy), Size(50f, 40f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f)) 
                    // Belly
                    drawRoundRect(bellyColor, Offset(dx + 10f, dy + 20f), Size(30f, 15f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f))
                    // Head
                    drawCircle(bodyColor, 18f, Offset(dx + 50f, dy + 10f))
                    // Eye
                    drawCircle(tOnBg, 6f, Offset(dx + 55f, dy + 5f))
                    drawCircle(tBg, 2f, Offset(dx + 55f + if(isDead) 0f else (kotlin.math.sin(gameTimeMs / 200.0) * 2f).toFloat(), dy + 5f))
                    // Curled Tail
                    drawArc(
                        color = bodyColor,
                        startAngle = 90f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(dx - 15f, dy + 15f),
                        size = Size(20f, 20f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    
                    // Animate legs
                    val leg1Y = if (isPlaying && (gameTimeMs / 100) % 2 == 0L) 30f else 40f
                    val leg2Y = if (isPlaying && (gameTimeMs / 100) % 2 != 0L) 30f else 40f
                    drawRoundRect(bodyColor, Offset(dx + 10f, dy + leg1Y), Size(8f, 50f - leg1Y), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f,4f)) // Leg 1
                    drawRoundRect(bodyColor, Offset(dx + 30f, dy + leg2Y), Size(8f, 50f - leg2Y), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f,4f)) // Leg 2
                }
                else -> { // Chameleon
                    val hsv = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.8f, 0.9f))
                    val bodyColor = if (isDead) Color.LightGray else Color(hsv)
                    val bellyColor = if (isDead) Color.Gray else bodyColor.copy(alpha = 0.6f)
                    
                    // Body
                    drawRoundRect(bodyColor, Offset(dx, dy), Size(50f, 40f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f)) 
                    // Belly
                    drawRoundRect(bellyColor, Offset(dx + 10f, dy + 20f), Size(30f, 15f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f))
                    // Head
                    drawCircle(bodyColor, 18f, Offset(dx + 50f, dy + 10f))
                    // Eye
                    drawCircle(Color.White, 6f, Offset(dx + 55f, dy + 5f))
                    drawCircle(Color.Black, 2f, Offset(dx + 55f + if(isDead) 0f else (kotlin.math.sin(gameTimeMs / 200.0) * 2f).toFloat(), dy + 5f))
                    // Curled Tail
                    drawArc(
                        color = bodyColor,
                        startAngle = 90f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(dx - 15f, dy + 15f),
                        size = Size(20f, 20f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    
                    // Animate legs
                    val leg1Y = if (isPlaying && (gameTimeMs / 100) % 2 == 0L) 30f else 40f
                    val leg2Y = if (isPlaying && (gameTimeMs / 100) % 2 != 0L) 30f else 40f
                    drawRoundRect(bodyColor, Offset(dx + 10f, dy + leg1Y), Size(8f, 50f - leg1Y), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f,4f)) // Leg 1
                    drawRoundRect(bodyColor, Offset(dx + 30f, dy + leg2Y), Size(8f, 50f - leg2Y), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f,4f)) // Leg 2
                }
            }
            
            if (obsType == 0) {
                // Ground Obstacles
                when (obsSkin) {
                    "Neon" -> {
                        val neonColor = Color(0xFF00E5FF)
                        drawRoundRect(neonColor, Offset(obsX, actualGround - 60f), Size(15f, 60f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f))
                        // Glow
                        drawRoundRect(neonColor.copy(alpha=0.3f), Offset(obsX - 5f, actualGround - 65f), Size(25f, 70f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f))
                    }
                    "City" -> {
                        val buildColor = Color.DarkGray
                        drawRect(buildColor, Offset(obsX, actualGround - 60f), Size(20f, 60f))
                        drawRect(Color.Yellow.copy(alpha=0.8f), Offset(obsX + 5f, actualGround - 50f), Size(5f,5f))
                        drawRect(Color.Yellow.copy(alpha=0.8f), Offset(obsX + 5f, actualGround - 30f), Size(5f,5f))
                    }
                    "Theme" -> {
                        val obColor = tTertiary
                        drawRoundRect(obColor, Offset(obsX, actualGround - 60f), Size(20f, 60f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f))
                        drawRoundRect(obColor, Offset(obsX - 10f, actualGround - 40f), Size(10f, 25f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f,5f))
                        drawRoundRect(obColor, Offset(obsX + 20f, actualGround - 50f), Size(10f, 30f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f,5f))
                    }
                    else -> { // Classic
                        val cactusColor = Color(0xFF2E7D32)
                        drawRoundRect(cactusColor, Offset(obsX, actualGround - 60f), Size(20f, 60f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f))
                        drawRoundRect(cactusColor, Offset(obsX - 10f, actualGround - 40f), Size(10f, 25f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f,5f))
                        drawRoundRect(cactusColor, Offset(obsX + 20f, actualGround - 50f), Size(10f, 30f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f,5f))
                    }
                }
            } else {
                // Flying Obstacles
                when (obsSkin) {
                    "Neon" -> {
                        val pX = obsX
                        val pY = actualGround - 90f
                        drawCircle(Color(0xFFFF007F), 8f, Offset(pX + 15f, pY + 5f)) // core
                        drawRoundRect(Color(0xFFFF007F).copy(alpha=0.4f), Offset(pX - 10f, pY), Size(50f, 10f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f,5f)) // laser
                    }
                    "City" -> {
                        val pX = obsX
                        val pY = actualGround - 90f
                        val droneColor = Color.LightGray
                        drawRoundRect(droneColor, Offset(pX, pY), Size(30f, 10f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f)) // Body
                        drawCircle(if((gameTimeMs/100)%2==0L) Color.Red else Color.DarkGray, 3f, Offset(pX + 5f, pY + 5f))
                        drawCircle(if((gameTimeMs/100)%2!=0L) Color.Red else Color.DarkGray, 3f, Offset(pX + 25f, pY + 5f))
                    }
                    "Theme" -> {
                        val pColor = tTertiary
                        val pX = obsX
                        val pY = actualGround - 90f
                        val wingY = if (isPlaying && (gameTimeMs / 150) % 2 == 0L) -15f else 10f
                        drawRoundRect(pColor, Offset(pX, pY), Size(30f, 10f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f)) // Body
                        drawRect(pColor, Offset(pX - 10f, pY + 2f), Size(10f, 6f)) // Head
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(pX + 10f, pY + 5f)
                            lineTo(pX + 20f, pY + wingY)
                            lineTo(pX + 30f, pY + 5f)
                            close()
                        }
                        drawPath(path, tOnBg)
                    }
                    else -> { // Classic
                        val pColor = Color.DarkGray
                        val pX = obsX
                        val pY = actualGround - 90f
                        val wingY = if (isPlaying && (gameTimeMs / 150) % 2 == 0L) -15f else 10f
                        drawRoundRect(pColor, Offset(pX, pY), Size(30f, 10f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f)) // Body
                        drawRect(pColor, Offset(pX - 10f, pY + 2f), Size(10f, 6f)) // Head
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(pX + 10f, pY + 5f)
                            lineTo(pX + 20f, pY + wingY)
                            lineTo(pX + 30f, pY + 5f)
                            close()
                        }
                        drawPath(path, Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun CardsGameView(viewModel: com.example.viewmodel.ChatViewModel? = null, mode: String = "single", peerId: String = "NONE", isHost: Boolean = true, difficulty: String = "medium") {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("theme_prefs", android.content.Context.MODE_PRIVATE) }
    var activeLang by remember { mutableStateOf(prefs.getString("selected_language", "en") ?: "en") }
    
    // Periodically sync language preference
    LaunchedEffect(Unit) {
        while (true) {
            val current = prefs.getString("selected_language", "en") ?: "en"
            if (current != activeLang) {
                activeLang = current
            }
            delay(500)
        }
    }

    // Colors & Skins Lists
    val bgs = listOf(
        Pair("felt_green", Pair(Color(0xFF1B5E20), Color(0xFF0D3C10))),
        Pair("mahogany", Pair(Color(0xFF3E2723), Color(0xFF1B0C0A))),
        Pair("royal_velvet", Pair(Color(0xFF4A148C), Color(0xFF1F0042))),
        Pair("dark_obsidian", Pair(Color(0xFF111111), Color(0xFF1F1F1F))),
        Pair("cyber_grid", Pair(Color(0xFF0F0C1B), Color(0xFF02000A))),
        Pair("sunset_orange", Pair(Color(0xFFD84315), Color(0xFF4E0D00)))
    )

    val bgNamesRu = mapOf(
        "felt_green" to "Зеленое сукно",
        "mahogany" to "Красное дерево",
        "royal_velvet" to "Королевский бархат",
        "dark_obsidian" to "Темный обсидиан",
        "cyber_grid" to "Кибер неон",
        "sunset_orange" to "Теплый закат"
    )

    val bgNamesEn = mapOf(
        "felt_green" to "Felt Green",
        "mahogany" to "Mahogany Wood",
        "royal_velvet" to "Royal Velvet",
        "dark_obsidian" to "Dark Obsidian",
        "cyber_grid" to "Cyber Neon",
        "sunset_orange" to "Sunset Warmth"
    )

    val skins = listOf("classic", "neon", "cyber", "ghostly", "theme_core", "emerald", "ruby", "sapphire", "amethyst")
    val skinNamesRu = mapOf(
        "classic" to "Классический красный",
        "neon" to "Неоновый синий",
        "cyber" to "Кибер золото",
        "ghostly" to "Призрачное серебро",
        "theme_core" to "Тема системы",
        "emerald" to "Изумрудный",
        "ruby" to "Рубиновый",
        "sapphire" to "Сапфировый",
        "amethyst" to "Аметистовый"
    )
    val skinNamesEn = mapOf(
        "classic" to "Classic Red",
        "neon" to "Neon Blue",
        "cyber" to "Cyber Gold",
        "ghostly" to "Ghostly Silver",
        "theme_core" to "System Theme",
        "emerald" to "Emerald Green",
        "ruby" to "Ruby Red",
        "sapphire" to "Sapphire Blue",
        "amethyst" to "Amethyst Purple"
    )

    // Game Preferences
    var selectedBg by remember { mutableStateOf(prefs.getString("durak_bg", "felt_green") ?: "felt_green") }
    var selectedSkin by remember { mutableStateOf(prefs.getString("durak_skin", "classic") ?: "classic") }

    // Durak state
    var deck by remember { mutableStateOf(emptyList<DurakCard>()) }
    var trumpCardByState by remember { mutableStateOf<DurakCard?>(null) }
    var playerHand by remember { mutableStateOf(emptyList<DurakCard>()) }
    var botHand by remember { mutableStateOf(emptyList<DurakCard>()) }
    var tablePairs by remember { mutableStateOf(emptyList<TablePair>()) }
    var discardedPileCount by remember { mutableStateOf(0) }
    var turnState by remember { mutableStateOf(DurakTurn.PLAYER_ATTACK) }
    var isBotThinking by remember { mutableStateOf(false) }
    var winnerMessage by remember { mutableStateOf<String?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    var trumpSuitState by remember { mutableStateOf("♠") }
    var isGameStarted by remember { mutableStateOf(false) }
    val trumpSuit = trumpSuitState
    val isMulti = mode == "multi"

    // Helper to get card by ID statically
    val ALL_CARDS = remember {
        (1..36).map { id ->
            val suits = listOf("♠", "♥", "♦", "♣")
            val ranks = listOf("6", "7", "8", "9", "10", "J", "Q", "K", "A")
            val suitIdx = (id - 1) / 9
            val rankIdx = (id - 1) % 9
            DurakCard(suits[suitIdx], ranks[rankIdx], id)
        }
    }

    fun getCardById(id: Int): DurakCard {
        return ALL_CARDS.firstOrNull { it.id == id } ?: ALL_CARDS[0]
    }

    fun parseCardIds(s: String): List<DurakCard> {
        if (s.isBlank() || s == "empty") return emptyList()
        return s.split(",").mapNotNull { idStr ->
            val id = idStr.toIntOrNull()
            if (id != null) getCardById(id) else null
        }
    }

    fun encodeTablePairs(pairs: List<TablePair>): String {
        if (pairs.isEmpty()) return "empty"
        return pairs.joinToString(",") { pair ->
            "${pair.attack.id}:${pair.defense?.id ?: "none"}"
        }
    }

    fun parseTablePairs(s: String): List<TablePair> {
        if (s == "empty" || s.isBlank()) return emptyList()
        return s.split(",").mapNotNull { pStr ->
            val parts = pStr.split(":")
            val attId = parts.getOrNull(0)?.toIntOrNull()
            if (attId != null) {
                val attCard = getCardById(attId)
                val defId = parts.getOrNull(1)?.toIntOrNull()
                val defCard = if (defId != null) getCardById(defId) else null
                TablePair(attack = attCard, defense = defCard)
            } else {
                null
            }
        }
    }

    fun broadcastState() {
        if (!isMulti || !isHost) return
        val hostCardsJoined = if (playerHand.isEmpty()) "empty" else playerHand.joinToString(",") { "${it.id}" }
        val guestCardsJoined = if (botHand.isEmpty()) "empty" else botHand.joinToString(",") { "${it.id}" }
        val deckJoined = if (deck.isEmpty()) "empty" else deck.joinToString(",") { "${it.id}" }
        val trumpId = trumpCardByState?.id ?: -1
        val turnIndex = if (turnState == DurakTurn.PLAYER_ATTACK) 1 else 2
        val encodedTable = encodeTablePairs(tablePairs)
        viewModel?.sendGameState("durak|syncState|$hostCardsJoined|$guestCardsJoined|$deckJoined|$trumpId|$turnIndex|$encodedTable|$trumpSuitState", peerId)
    }

    // Text translations
    val texts = mapOf(
        "en" to mapOf(
            "title" to "Durak Card Game",
            "bot_turn_attack" to "Bot is attacking! Defend!",
            "player_turn_attack" to "Your turn to attack! Tap card to play.",
            "bot_thinking" to "Bot is thinking...",
            "deck_left" to "Deck count: ",
            "trump" to "Trump:",
            "bito" to "Pass / Done",
            "take" to "Take Cards",
            "restart" to "Restart",
            "win" to "You Won! 🎉 King of Durak!",
            "lose" to "Bot Won! You are the Durak! 🃏",
            "draw" to "Draw! No Duraks today.",
            "discard_size" to "Discard pile: ",
            "invalid_defense" to "Can't beat this! Use higher card or trump.",
            "invalid_attack" to "Attack invalid! Card rank must match cards on table.",
            "select_bg" to "Bg: ",
            "select_skin" to "Cover: ",
            "bot_hand" to "Bot Cards: ",
            "your_hand" to "Your Cards",
            "bot_passed" to "Bot passed! Round ends.",
            "bot_took" to "Bot can't defend! Bot takes cards.",
            "opponent_hand" to "Opponent: ",
            "opponent_win" to "You Won! 🎉 King of Durak!",
            "opponent_lose" to "Opponent Won! You are Durak! 🃏",
            "opponent_turn_attack" to "Opponent is attacking! Defend!",
            "opponent_thinking" to "Waiting for opponent...",
            "bot_transferred" to "Bot transferred the attack to you!",
            "invalid_transfer" to "Can't transfer! Opponent doesn't have enough cards."
        ),
        "ru" to mapOf(
            "title" to "Карточная Игра Дурак",
            "bot_turn_attack" to "Бот нападает! Защищайтесь!",
            "player_turn_attack" to "Ваш ход атак! Тапните по карте стола.",
            "bot_thinking" to "Бот размышляет...",
            "deck_left" to "Карт в колоде: ",
            "trump" to "Козырь:",
            "bito" to "Бито / Пас",
            "take" to "Взять карты",
            "restart" to "Начать заново",
            "win" to "Вы выиграли! 🎉 Босс стола!",
            "lose" to "Бот выиграл! Вы остались в дураках! 🃏",
            "draw" to "Ничья! Никого в дураках.",
            "discard_size" to "В бито: ",
            "invalid_defense" to "Нельзя побить! Нужна старшая или козырь.",
            "invalid_attack" to "Неверная атака! Ранг должен быть на столе.",
            "select_bg" to "Стол: ",
            "select_skin" to "Рубашка: ",
            "bot_hand" to "Оппонент: ",
            "your_hand" to "Ваши карты",
            "bot_passed" to "Бот спасовал! Раунд закончен.",
            "bot_took" to "Бот не может побить и берет карты.",
            "opponent_hand" to "Соперник: ",
            "opponent_win" to "Вы выиграли! 🎉 Босс стола!",
            "opponent_lose" to "Соперник выиграл! Вы остались в дураках! 🃏",
            "opponent_turn_attack" to "Соперник нападает! Защищайтесь!",
            "opponent_thinking" to "Ожидание хода соперника...",
            "bot_transferred" to "Бот перевёл карты на вас!",
            "invalid_transfer" to "Нельзя перевести! У соперника мало карт."
        )
    )

    fun getTxt(key: String): String {
        return texts[activeLang]?.get(key) ?: key
    }

    // Helper functions
    fun canBeat(attack: DurakCard, defense: DurakCard, trump: String): Boolean {
        if (defense.suit == attack.suit) {
            return defense.rankValue > attack.rankValue
        }
        if (defense.suit == trump && attack.suit != trump) {
            return true
        }
        return false
    }

    // Start/Reset Game
    fun initGame() {
        val fullDeck = mutableListOf<DurakCard>()
        val suits = listOf("♠", "♥", "♦", "♣")
        val ranks = listOf("6", "7", "8", "9", "10", "J", "Q", "K", "A")
        var cardId = 1
        for (suit in suits) {
            for (rank in ranks) {
                fullDeck.add(DurakCard(suit, rank, cardId++))
            }
        }
        fullDeck.shuffle()

        val pHand = fullDeck.take(6).toMutableList()
        val bHand = fullDeck.drop(6).take(6).toMutableList()
        val remaining = fullDeck.drop(12)
        val tCard = remaining.lastOrNull()
        val computedTrumpSuit = tCard?.suit ?: "♠"

        pHand.sortWith(compareBy({ it.suit == computedTrumpSuit }, { it.rankValue }))
        bHand.sortWith(compareBy({ it.suit == computedTrumpSuit }, { it.rankValue }))

        playerHand = pHand
        botHand = bHand
        deck = remaining
        trumpCardByState = tCard
        trumpSuitState = computedTrumpSuit
        tablePairs = emptyList()
        discardedPileCount = 0
        winnerMessage = null
        toastMessage = null

        // Determine who has the lowest trump card to start
        val pTrumps = pHand.filter { it.suit == computedTrumpSuit }
        val bTrumps = bHand.filter { it.suit == computedTrumpSuit }

        val minP = pTrumps.minOfOrNull { it.rankValue } ?: 999
        val minB = bTrumps.minOfOrNull { it.rankValue } ?: 999

        if (minB < minP) {
            turnState = DurakTurn.PLAYER_DEFEND
            isBotThinking = true
        } else {
            turnState = DurakTurn.PLAYER_ATTACK
            isBotThinking = false
        }
        isGameStarted = true
    }

    // Trigger Initializer
    if (!isGameStarted && winnerMessage == null) {
        if (!isMulti || isHost) {
            initGame()
        }
    }

    // Check game outcome
    fun verifyEnd() {
        if (deck.isEmpty() && trumpCardByState == null) {
            if (playerHand.isEmpty() && botHand.isEmpty()) {
                winnerMessage = getTxt("draw")
            } else if (playerHand.isEmpty() && botHand.isNotEmpty()) {
                winnerMessage = if (isMulti) getTxt("opponent_win") else getTxt("win")
            } else if (botHand.isEmpty() && playerHand.isNotEmpty()) {
                winnerMessage = if (isMulti) getTxt("opponent_lose") else getTxt("lose")
            }
        }
    }

    // Replenish hands up to 6 cards
    fun refillHands(attackersTurn: DurakTurn) {
        var currentDeck = deck.toMutableList()
        val pNeeded = 6 - playerHand.size
        val bNeeded = 6 - botHand.size
        val newPHand = playerHand.toMutableList()
        val newBHand = botHand.toMutableList()

        if (attackersTurn == DurakTurn.PLAYER_ATTACK) {
            // Player attacks, draws first
            if (pNeeded > 0) {
                val draws = currentDeck.take(pNeeded)
                currentDeck = currentDeck.drop(draws.size).toMutableList()
                newPHand.addAll(draws)
                if (newPHand.size < 6 && currentDeck.isEmpty() && trumpCardByState != null) {
                    newPHand.add(trumpCardByState!!)
                    trumpCardByState = null
                }
            }
            if (bNeeded > 0) {
                val draws = currentDeck.take(bNeeded)
                currentDeck = currentDeck.drop(draws.size).toMutableList()
                newBHand.addAll(draws)
                if (newBHand.size < 6 && currentDeck.isEmpty() && trumpCardByState != null) {
                    newBHand.add(trumpCardByState!!)
                    trumpCardByState = null
                }
            }
        } else {
            // Bot attacks, draws first
            if (bNeeded > 0) {
                val draws = currentDeck.take(bNeeded)
                currentDeck = currentDeck.drop(draws.size).toMutableList()
                newBHand.addAll(draws)
                if (newBHand.size < 6 && currentDeck.isEmpty() && trumpCardByState != null) {
                    newBHand.add(trumpCardByState!!)
                    trumpCardByState = null
                }
            }
            if (pNeeded > 0) {
                val draws = currentDeck.take(pNeeded)
                currentDeck = currentDeck.drop(draws.size).toMutableList()
                newPHand.addAll(draws)
                if (newPHand.size < 6 && currentDeck.isEmpty() && trumpCardByState != null) {
                    newPHand.add(trumpCardByState!!)
                    trumpCardByState = null
                }
            }
        }

        playerHand = newPHand.sortedWith(compareBy({ it.suit == trumpSuit }, { it.rankValue }))
        botHand = newBHand.sortedWith(compareBy({ it.suit == trumpSuit }, { it.rankValue }))
        deck = currentDeck
        verifyEnd()
    }

    // Player action: Pass / Done attacking
    fun onPlayerPass() {
        if (tablePairs.isEmpty()) return
        if (tablePairs.all { it.defense != null }) {
            // Discard table pairs
            discardedPileCount += tablePairs.flatMap { listOf(it.attack, it.defense).filterNotNull() }.size
            tablePairs = emptyList()
            refillHands(DurakTurn.PLAYER_ATTACK)
            // Bot becomes attacker
            turnState = DurakTurn.PLAYER_DEFEND
            isBotThinking = true
        }
    }

    // Player action: Take all cards because they can't defend
    fun onPlayerTake() {
        if (tablePairs.isEmpty()) return
        val allCards = tablePairs.flatMap { listOf(it.attack, it.defense).filterNotNull() }
        playerHand = (playerHand + allCards).sortedWith(compareBy({ it.suit == trumpSuit }, { it.rankValue }))
        tablePairs = emptyList()
        refillHands(DurakTurn.PLAYER_DEFEND)
        // Bot remains attacker
        turnState = DurakTurn.PLAYER_DEFEND
        isBotThinking = true
    }

    // Multiplayer Network state collection & handling
    val gameStateFlow = viewModel?.gameStateFlow
    LaunchedEffect(gameStateFlow) {
        if (isMulti) {
            gameStateFlow?.collect { payload ->
                val parts = payload.split("|")
                if (parts[0] == "durak" && parts.size >= 2) {
                    when (parts[1]) {
                        "syncState" -> {
                            if (!isHost) {
                                val hostCardsStr = parts.getOrNull(2) ?: "empty"
                                val guestCardsStr = parts.getOrNull(3) ?: "empty"
                                val deckStr = parts.getOrNull(4) ?: "empty"
                                val trumpId = parts.getOrNull(5)?.toIntOrNull() ?: -1
                                val turnIndex = parts.getOrNull(6)?.toIntOrNull() ?: 1
                                val encodedTable = parts.getOrNull(7) ?: "empty"
                                val syncedTrumpSuit = parts.getOrNull(8) ?: "♠"

                                playerHand = parseCardIds(guestCardsStr)
                                botHand = parseCardIds(hostCardsStr)
                                deck = parseCardIds(deckStr)
                                trumpCardByState = if (trumpId != -1) getCardById(trumpId) else null
                                trumpSuitState = syncedTrumpSuit
                                tablePairs = parseTablePairs(encodedTable)
                                turnState = if (turnIndex == 2) DurakTurn.PLAYER_ATTACK else DurakTurn.PLAYER_DEFEND
                                isBotThinking = false
                            }
                        }
                        "requestAttack" -> {
                            if (isHost) {
                                val cardId = parts.getOrNull(2)?.toIntOrNull()
                                if (cardId != null) {
                                    val card = botHand.firstOrNull { it.id == cardId }
                                    if (card != null) {
                                        botHand = botHand - card
                                        tablePairs = tablePairs + TablePair(card)
                                        broadcastState()
                                    }
                                }
                            }
                        }
                        "requestDefend" -> {
                            if (isHost) {
                                val defendCardId = parts.getOrNull(2)?.toIntOrNull()
                                val attackCardId = parts.getOrNull(3)?.toIntOrNull()
                                if (defendCardId != null && attackCardId != null) {
                                    val defCard = botHand.firstOrNull { it.id == defendCardId }
                                    if (defCard != null) {
                                        val updated = tablePairs.toMutableList()
                                        val idx = updated.indexOfFirst { it.attack.id == attackCardId && it.defense == null }
                                        if (idx != -1) {
                                            updated[idx] = updated[idx].copy(defense = defCard)
                                            tablePairs = updated
                                            botHand = botHand - defCard
                                            broadcastState()
                                        }
                                    }
                                }
                            }
                        }
                        "requestTransfer" -> {
                            if (isHost) {
                                val cardId = parts.getOrNull(2)?.toIntOrNull()
                                if (cardId != null) {
                                    val card = botHand.firstOrNull { it.id == cardId }
                                    if (card != null) {
                                        botHand = botHand - card
                                        tablePairs = tablePairs + TablePair(card)
                                        turnState = DurakTurn.PLAYER_DEFEND
                                        broadcastState()
                                    }
                                }
                            }
                        }
                        "requestBito" -> {
                            if (isHost) {
                                discardedPileCount += tablePairs.flatMap { listOf(it.attack, it.defense).filterNotNull() }.size
                                tablePairs = emptyList()
                                refillHands(DurakTurn.PLAYER_DEFEND)
                                turnState = DurakTurn.PLAYER_ATTACK
                                broadcastState()
                            }
                        }
                        "requestTake" -> {
                            if (isHost) {
                                val taken = tablePairs.flatMap { listOf(it.attack, it.defense).filterNotNull() }
                                botHand = (botHand + taken).sortedWith(compareBy({ it.suit == trumpSuit }, { it.rankValue }))
                                tablePairs = emptyList()
                                refillHands(DurakTurn.PLAYER_ATTACK)
                                turnState = DurakTurn.PLAYER_ATTACK
                                broadcastState()
                            }
                        }
                        "requestRestart" -> {
                            if (isHost) {
                                initGame()
                                broadcastState()
                            }
                        }
                    }
                }
            }
        }
    }

    // Initializer to start or request initialization
    LaunchedEffect(isMulti) {
        if (isMulti) {
            if (isHost) {
                initGame()
                delay(800)
                broadcastState()
            } else {
                delay(300)
                viewModel?.sendGameState("durak|requestRestart", peerId)
            }
        }
    }

    // Bot Attacks or Tof-Ins
    LaunchedEffect(turnState, tablePairs, isBotThinking) {
        if (isMulti) return@LaunchedEffect
        if (winnerMessage != null) return@LaunchedEffect
        
        if (turnState == DurakTurn.PLAYER_DEFEND && isBotThinking) {
            delay(1200)
            if (tablePairs.isEmpty()) {
                // Initial Bot Attack: lowest non-trump card
                val targetCard = botHand.minWithOrNull(compareBy({ it.suit == trumpSuit }, { it.rankValue }))
                if (targetCard != null) {
                    botHand = botHand - targetCard
                    tablePairs = tablePairs + TablePair(targetCard)
                }
                isBotThinking = false
            } else if (tablePairs.all { it.defense != null }) {
                // Toss-in check: bot tosses in if bot hand contains any matches with table cards
                val activeRanks = tablePairs.flatMap { listOf(it.attack.rank, it.defense?.rank).filterNotNull() }.toSet()
                val candidates = botHand.filter { it.rank in activeRanks && (it.suit != trumpSuit || it.rankValue < 12) }
                
                if (candidates.isNotEmpty() && tablePairs.size < 6) {
                    val addCard = candidates.minByOrNull { it.rankValue }!!
                    botHand = botHand - addCard
                    tablePairs = tablePairs + TablePair(addCard)
                    isBotThinking = false
                } else {
                    // Bot passes! Turn ends.
                    delay(500)
                    toastMessage = getTxt("bot_passed")
                    discardedPileCount += tablePairs.flatMap { listOf(it.attack, it.defense).filterNotNull() }.size
                    tablePairs = emptyList()
                    refillHands(DurakTurn.PLAYER_DEFEND)
                    turnState = DurakTurn.PLAYER_ATTACK
                    isBotThinking = false
                }
            }
        }
    }

    // Bot Defends Player's attack
    LaunchedEffect(tablePairs, turnState) {
        if (isMulti) return@LaunchedEffect
        if (winnerMessage != null) return@LaunchedEffect
        
        if (turnState == DurakTurn.PLAYER_ATTACK && tablePairs.isNotEmpty() && tablePairs.any { it.defense == null }) {
            isBotThinking = true
            delay(1000)
            
            // Check if Bot can transfer (Перевод)
            val canTransfer = tablePairs.all { it.defense == null } && (tablePairs.size + 1 <= playerHand.size)
            val transferCard = if (canTransfer) botHand.firstOrNull { it.rank == tablePairs.first().attack.rank } else null
            
            if (transferCard != null) {
                toastMessage = getTxt("bot_transferred")
                botHand = botHand - transferCard
                tablePairs = tablePairs + TablePair(transferCard)
                turnState = DurakTurn.PLAYER_DEFEND
                isBotThinking = false
                return@LaunchedEffect
            }
            
            val updatedPairs = tablePairs.toMutableList()
            var currentBotHand = botHand.toMutableList()
            var couldDefendAll = true

            for (i in updatedPairs.indices) {
                val pair = updatedPairs[i]
                if (pair.defense == null) {
                    val att = pair.attack
                    val validDefenders = currentBotHand.filter { canBeat(att, it, trumpSuit) }
                    val bSuit = att.suit
                    val bestDef = validDefenders.minWithOrNull(compareBy({ it.suit == trumpSuit }, { it.rankValue }))
                    
                    if (bestDef != null) {
                        currentBotHand.remove(bestDef)
                        updatedPairs[i] = pair.copy(defense = bestDef)
                    } else {
                        couldDefendAll = false
                        break
                    }
                }
            }

            if (couldDefendAll) {
                botHand = currentBotHand
                tablePairs = updatedPairs
                toastMessage = null
            } else {
                // Bot cannot defend, takes all cards!
                toastMessage = getTxt("bot_took")
                val taken = tablePairs.flatMap { listOf(it.attack, it.defense).filterNotNull() }
                botHand = (botHand + taken).sortedWith(compareBy({ it.suit == trumpSuit }, { it.rankValue }))
                tablePairs = emptyList()
                refillHands(DurakTurn.PLAYER_ATTACK)
                // Player attacks again!
                turnState = DurakTurn.PLAYER_ATTACK
            }
            isBotThinking = false
        }
    }

    // Colors according to selectedBg
    val selectedBgColors = bgs.firstOrNull { it.first == selectedBg }?.second ?: Pair(Color(0xFF1B5E20), Color(0xFF0D3C10))
    val currentBgColors = selectedBgColors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(currentBgColors.first, currentBgColors.second)
                )
            )
    ) {
        // Draw cyber neon borders if selectedBg is cyber_grid
        if (selectedBg == "cyber_grid") {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 40.dp.toPx()
                for (x in 0..(size.width / step).toInt()) {
                    drawLine(
                        color = Color(0xFF00E5FF).copy(alpha = 0.08f),
                        start = Offset(x * step, 0f),
                        end = Offset(x * step, size.height),
                        strokeWidth = 1f
                    )
                }
                for (y in 0..(size.height / step).toInt()) {
                    drawLine(
                        color = Color(0xFFFF007F).copy(alpha = 0.08f),
                        start = Offset(0f, y * step),
                        end = Offset(size.width, y * step),
                        strokeWidth = 1f
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            
            // TOP BAR: Title, Reset & Settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = getTxt("title"),
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        text = if (isMulti) "${getTxt("opponent_hand")} ${botHand.size}" else "${getTxt("bot_hand")} ${botHand.size}",
                        color = Color.White.copy(alpha=0.7f),
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        onClick = {
                            if (isMulti) {
                                if (isHost) {
                                    initGame()
                                    broadcastState()
                                } else {
                                    viewModel?.sendGameState("durak|requestRestart", peerId)
                                }
                            } else {
                                initGame()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha=0.2f))
                    ) {
                        Text(getTxt("restart"), color = Color.White, fontSize = 11.sp)
                    }
                }
            }

            // SETTINGS PANEL: Quick selector of covers & backdrops
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .background(Color.Black.copy(alpha = 0.3f), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Backdrop option list
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(getTxt("select_bg"), color = Color.Yellow, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        bgs.forEach { bgOption ->
                            val isSelected = selectedBg == bgOption.first
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(bgOption.second.first, androidx.compose.foundation.shape.CircleShape)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                    .clickable {
                                        selectedBg = bgOption.first
                                        prefs.edit().putString("durak_bg", bgOption.first).apply()
                                    }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha=0.3f)))

                // Language Selector
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (activeLang == "en") "LANGUAGE" else "ЯЗЫК", color = Color.Yellow, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Button(
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        onClick = {
                            val nextLang = if (activeLang == "en") "ru" else "en"
                            activeLang = nextLang
                            prefs.edit().putString("selected_language", nextLang).apply()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha=0.15f))
                    ) {
                        Text(if (activeLang == "en") "RU" else "EN", color = Color.White, fontSize = 10.sp)
                    }
                }

                Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color.White.copy(alpha=0.3f)))

                // Card Skin option cycle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(getTxt("select_skin"), color = Color.Yellow, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Button(
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        onClick = {
                            val nextSkin = skins[(skins.indexOf(selectedSkin) + 1) % skins.size]
                            selectedSkin = nextSkin
                            prefs.edit().putString("durak_skin", nextSkin).apply()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha=0.15f))
                    ) {
                        val skinLabel = if (activeLang == "en") skinNamesEn[selectedSkin] ?: selectedSkin else skinNamesRu[selectedSkin] ?: selectedSkin
                        Text(skinLabel.substringBefore(" "), color = Color.White, fontSize = 10.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // OPPONENT ROW: Render bot cards overlappingly
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy((-18).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    botHand.forEach { card ->
                        DurakCardRender(
                            card = card,
                            skinId = selectedSkin,
                            isFaceUp = false,
                            modifier = Modifier.size(width = 46.dp, height = 64.dp)
                        )
                    }
                }
            }

            // CENTRAL ATTACK & DEFENSE TABLE AREA
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Table Cards Layout
                if (tablePairs.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        tablePairs.chunked(3).forEach { rowPairs ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                rowPairs.forEach { pair ->
                                    Box(modifier = Modifier.size(width = 75.dp, height = 115.dp)) {
                                        DurakCardRender(card = pair.attack, skinId = selectedSkin, isFaceUp = true)
                                        pair.defense?.let { def ->
                                            Box(
                                                modifier = Modifier
                                                    .offset(x = 12.dp, y = 14.dp)
                                                    .size(width = 75.dp, height = 115.dp)
                                            ) {
                                                DurakCardRender(card = def, skinId = selectedSkin, isFaceUp = true)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Show central felt circle or table message
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .border(2.dp, Color.White.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Style,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "DURAK",
                                color = Color.White.copy(alpha = 0.15f),
                                fontSize = 16.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // BOT STATUS / ADVISORY & PILES
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info Toast
                Text(
                    text = if (isBotThinking) (if (isMulti) getTxt("opponent_thinking") else getTxt("bot_thinking"))
                           else toastMessage ?: (if (turnState == DurakTurn.PLAYER_ATTACK) getTxt("player_turn_attack") else (if (isMulti) getTxt("opponent_turn_attack") else getTxt("bot_turn_attack"))),
                    color = if (toastMessage != null) Color(0xFFFFEB3B) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // Trump & Deck Stack
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val trumpColor = if (trumpSuit == "♥" || trumpSuit == "♦") Color(0xFFFF5252) else Color(0xFF40C4FF)
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = getTxt("trump"),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                        Text(
                            text = trumpSuit,
                            color = trumpColor,
                            fontSize = 14.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }

                    Text(
                        text = "${getTxt("deck_left")} ${deck.size}",
                        color = Color.White.copy(alpha=0.8f),
                        fontSize = 11.sp
                    )

                    trumpCardByState?.let { trump ->
                        Box(contentAlignment = Alignment.Center) {
                            DurakCardRender(
                                card = trump,
                                skinId = selectedSkin,
                                isFaceUp = true,
                                modifier = Modifier
                                    .graphicsLayer { rotationZ = 90f }
                                    .size(width = 30.dp, height = 44.dp)
                            )
                        }
                    }
                }
            }

            // MAIN INTERACTION ROW: PASS / TAKE BUTTONS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // BITO Button
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (isMulti) {
                            if (isHost) {
                                discardedPileCount += tablePairs.flatMap { listOf(it.attack, it.defense).filterNotNull() }.size
                                tablePairs = emptyList()
                                refillHands(DurakTurn.PLAYER_ATTACK)
                                turnState = DurakTurn.PLAYER_DEFEND
                                broadcastState()
                            } else {
                                viewModel?.sendGameState("durak|requestBito", peerId)
                                isBotThinking = true
                            }
                        } else {
                            onPlayerPass()
                        }
                    },
                    enabled = turnState == DurakTurn.PLAYER_ATTACK && tablePairs.isNotEmpty() && tablePairs.all { it.defense != null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50),
                        disabledContainerColor = Color.White.copy(alpha=0.1f)
                    )
                ) {
                    Text(getTxt("bito"), fontSize = 13.sp, color = Color.White)
                }

                // TAKE Button
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (isMulti) {
                            if (isHost) {
                                val taken = tablePairs.flatMap { listOf(it.attack, it.defense).filterNotNull() }
                                playerHand = (playerHand + taken).sortedWith(compareBy({ it.suit == trumpSuit }, { it.rankValue }))
                                tablePairs = emptyList()
                                refillHands(DurakTurn.PLAYER_DEFEND)
                                turnState = DurakTurn.PLAYER_DEFEND
                                broadcastState()
                            } else {
                                viewModel?.sendGameState("durak|requestTake", peerId)
                                isBotThinking = true
                            }
                        } else {
                            onPlayerTake()
                        }
                    },
                    enabled = turnState == DurakTurn.PLAYER_DEFEND && tablePairs.isNotEmpty() && tablePairs.any { it.defense == null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935),
                        disabledContainerColor = Color.White.copy(alpha=0.1f)
                    )
                ) {
                    Text(getTxt("take"), fontSize = 13.sp, color = Color.White)
                }
            }

            // HUMAN HAND AREA: horizontal scrolling or overlapping card layout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.2f))
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = getTxt("your_hand"),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy((-16).dp), // Cozy overlap spacing
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(playerHand) { card ->
                            val isEligible = when (turnState) {
                                DurakTurn.PLAYER_ATTACK -> {
                                    tablePairs.isEmpty() || tablePairs.flatMap { listOf(it.attack.rank, it.defense?.rank).filterNotNull() }.contains(card.rank)
                                }
                                DurakTurn.PLAYER_DEFEND -> {
                                    val firstUndefended = tablePairs.firstOrNull { it.defense == null }
                                    val canBeatCard = firstUndefended != null && canBeat(firstUndefended.attack, card, trumpSuit)
                                    val canTransfer = tablePairs.isNotEmpty() &&
                                            tablePairs.all { it.defense == null } &&
                                            card.rank == tablePairs.first().attack.rank &&
                                            tablePairs.size + 1 <= botHand.size
                                    canBeatCard || canTransfer
                                }
                            }

                            DurakCardRender(
                                card = card,
                                skinId = selectedSkin,
                                isFaceUp = true,
                                modifier = Modifier
                                    .size(width = 75.dp, height = 115.dp)
                                    .graphicsLayer {
                                        // Pop up eligible playable cards slightly
                                        if (isEligible && !isBotThinking) {
                                            translationY = -15f
                                        }
                                    }
                                    .clickable {
                                        if (isBotThinking) return@clickable
                                        
                                        if (turnState == DurakTurn.PLAYER_ATTACK) {
                                            if (tablePairs.isEmpty() || tablePairs.flatMap { listOf(it.attack.rank, it.defense?.rank).filterNotNull() }.contains(card.rank)) {
                                                if (tablePairs.size < 6) {
                                                    if (isMulti) {
                                                        if (isHost) {
                                                            playerHand = playerHand - card
                                                            tablePairs = tablePairs + TablePair(card)
                                                            broadcastState()
                                                        } else {
                                                            viewModel?.sendGameState("durak|requestAttack|${card.id}", peerId)
                                                            isBotThinking = true
                                                        }
                                                    } else {
                                                        playerHand = playerHand - card
                                                        tablePairs = tablePairs + TablePair(card)
                                                        toastMessage = null
                                                    }
                                                }
                                            } else {
                                                toastMessage = getTxt("invalid_attack")
                                            }
                                        } else {
                                            // Handle defense or transfer
                                            val canTransfer = tablePairs.isNotEmpty() &&
                                                    tablePairs.all { it.defense == null } &&
                                                    card.rank == tablePairs.first().attack.rank
                                            
                                            if (canTransfer) {
                                                if (tablePairs.size + 1 <= botHand.size) {
                                                    if (isMulti) {
                                                        if (isHost) {
                                                            playerHand = playerHand - card
                                                            tablePairs = tablePairs + TablePair(card)
                                                            turnState = DurakTurn.PLAYER_ATTACK
                                                            broadcastState()
                                                        } else {
                                                            viewModel?.sendGameState("durak|requestTransfer|${card.id}", peerId)
                                                            isBotThinking = true
                                                        }
                                                    } else {
                                                        playerHand = playerHand - card
                                                        tablePairs = tablePairs + TablePair(card)
                                                        turnState = DurakTurn.PLAYER_ATTACK
                                                        toastMessage = null
                                                    }
                                                } else {
                                                    toastMessage = getTxt("invalid_transfer")
                                                }
                                            } else {
                                                // Handle regular defense selection
                                                val undefended = tablePairs.firstOrNull { it.defense == null }
                                                if (undefended != null) {
                                                    if (canBeat(undefended.attack, card, trumpSuit)) {
                                                        if (isMulti) {
                                                            if (isHost) {
                                                                val updated = tablePairs.toMutableList()
                                                                val idx = updated.indexOfFirst { it.attack.id == undefended.attack.id && it.defense == null }
                                                                if (idx != -1) {
                                                                    updated[idx] = updated[idx].copy(defense = card)
                                                                    tablePairs = updated
                                                                    playerHand = playerHand - card
                                                                    broadcastState()
                                                                }
                                                            } else {
                                                                viewModel?.sendGameState("durak|requestDefend|${card.id}|${undefended.attack.id}", peerId)
                                                                isBotThinking = true
                                                            }
                                                        } else {
                                                            val updated = tablePairs.toMutableList()
                                                            val idx = updated.indexOfFirst { it.attack == undefended.attack && it.defense == null }
                                                            if (idx != -1) {
                                                                updated[idx] = undefended.copy(defense = card)
                                                                tablePairs = updated
                                                                playerHand = playerHand - card
                                                                toastMessage = null
                                                                if (updated.all { it.defense != null }) {
                                                                    isBotThinking = true
                                                                }
                                                            }
                                                        }
                                                    } else {
                                                        toastMessage = getTxt("invalid_defense")
                                                        }
                                                    }
                                                }
                                            }
                                        }
                            )
                        }
                    }
                }
            }
        }

        // OUTCOME DIALOG: Draw / Win / Lose Overlay
        if (winnerMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = winnerMessage!!,
                            fontSize = 22.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                if (isMulti) {
                                    if (isHost) {
                                        initGame()
                                        broadcastState()
                                    } else {
                                        viewModel?.sendGameState("durak|requestRestart", peerId)
                                    }
                                } else {
                                    initGame()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(getTxt("restart"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DurakCardRender(
    card: DurakCard,
    skinId: String,
    isFaceUp: Boolean,
    modifier: Modifier = Modifier
) {
    val isRed = card.isRed
    
    // Core color schemes on modern adaptive cards
    val cardBg = when (skinId) {
        "neon" -> Color(0xFF0F0018)
        "cyber" -> Color(0xFF1E1E24)
        "ghostly" -> Color(0xEAD0DFFF)
        "emerald", "ruby", "sapphire", "amethyst" -> Color(0xFFFAF9F6)
        "theme_core" -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.White
    }

    val backColor = when (skinId) {
        "neon" -> Color(0xFFE040FB)
        "cyber" -> Color(0xFFFFD700)
        "ghostly" -> Color(0xFF546E7A)
        "emerald" -> Color(0xFF2E7D32)
        "ruby" -> Color(0xFFC62828)
        "sapphire" -> Color(0xFF1565C0)
        "amethyst" -> Color(0xFF6A1B9A)
        "theme_core" -> MaterialTheme.colorScheme.primary
        else -> Color(0xFFD32F2F) // Classic red back
    }

    val outlineColor = when (skinId) {
        "neon" -> Color(0xFF00E5FF)
        "cyber" -> Color(0xFFFFD700)
        "ghostly" -> Color(0xFF90A4AE)
        "emerald" -> Color(0xFF4CAF50)
        "ruby" -> Color(0xFFE53935)
        "sapphire" -> Color(0xFF1E88E5)
        "amethyst" -> Color(0xFF8E24AA)
        "theme_core" -> MaterialTheme.colorScheme.outline
        else -> Color(0xFFE0E0E0)
    }

    val contentColor = if (isRed) {
        when (skinId) {
            "neon" -> Color(0xFFFF1744)
            "cyber" -> Color(0xFFFF5252)
            else -> Color(0xFFE53935)
        }
    } else {
        when (skinId) {
            "neon" -> Color(0xFF00E5FF)
            "cyber" -> Color(0xFFFFD700)
            "ghostly" -> Color(0xFF37474F)
            "theme_core" -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> Color(0xFF212121)
        }
    }

    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = if (isFaceUp) cardBg else backColor,
        modifier = modifier
            .border(
                width = 1.3.dp,
                color = outlineColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .shadow(elevation = 3.dp, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
    ) {
        if (isFaceUp) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                // Suit & Rank in Corners
                Text(
                    text = "${card.rank}\n${card.suit}",
                    color = contentColor,
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart)
                )

                // Central high-contrast readable symbol
                Text(
                    text = card.suit,
                    color = contentColor.copy(alpha = 0.2f),
                    fontSize = 40.sp,
                    modifier = Modifier.align(Alignment.Center)
                )

                Text(
                    text = "${card.rank}\n${card.suit}",
                    color = contentColor,
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .graphicsLayer { rotationZ = 180f }
                )
            }
        } else {
            // Textured dynamic customizable "Skin" Card Back
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 2.dp.toPx()
                    when (skinId) {
                        "cyber" -> {
                            drawCircle(
                                color = Color.Black.copy(alpha=0.3f),
                                radius = size.minDimension / 4f,
                                center = center
                            )
                        }
                        "neon" -> {
                            drawLine(
                                color = Color.White.copy(alpha=0.4f),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height),
                                strokeWidth = stroke
                            )
                            drawLine(
                                color = Color.White.copy(alpha=0.4f),
                                start = Offset(size.width, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = stroke
                            )
                        }
                        "ghostly" -> {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.2f),
                                radius = size.minDimension * 0.35f,
                                center = center,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                            )
                        }
                        else -> {
                            // Classic Ornate frame + centered beautiful diamond grid watermark
                            drawRect(
                                color = Color.White.copy(alpha=0.25f),
                                topLeft = Offset(size.width*0.12f, size.height*0.12f),
                                size = Size(size.width*0.76f, size.height*0.76f),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                            )
                            drawCircle(
                                color = Color.White.copy(alpha=0.15f),
                                radius = size.minDimension*0.22f,
                                center = center
                            )
                        }
                    }
                }
            }
        }
    }
}

// Data models & enums for Durak
data class DurakCard(
    val suit: String, // "♠", "♥", "♦", "♣"
    val rank: String, // "6", "7", "8", "9", "10", "J", "Q", "K", "A"
    val id: Int
) {
    val isRed = suit == "♥" || suit == "♦"
    val rankValue: Int
        get() = when (rank) {
            "6" -> 6
            "7" -> 7
            "8" -> 8
            "9" -> 9
            "10" -> 10
            "J" -> 11
            "Q" -> 12
            "K" -> 13
            "A" -> 14
            else -> 0
        }
}

data class TablePair(
    val attack: DurakCard,
    var defense: DurakCard? = null
)

enum class DurakTurn { PLAYER_ATTACK, PLAYER_DEFEND }

