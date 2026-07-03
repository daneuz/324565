package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viewmodel.BleViewModel
import androidx.navigation.NavHostController
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.network.Message
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ThemeManager
import com.example.viewmodel.ChatViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {
    private lateinit var themeManager: ThemeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeManager = ThemeManager(this)
        
        var sharedUri: android.net.Uri? = null
        if (intent?.action == android.content.Intent.ACTION_SEND) {
            sharedUri = intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
        }
        
        enableEdgeToEdge()
        setContent {
            val appTheme by themeManager.currentTheme.collectAsState()
            MyApplicationTheme(appTheme = appTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MeshApp(themeManager, sharedUri)
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MeshApp(themeManager: ThemeManager, sharedUri: android.net.Uri? = null) {
    val permissionList = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE
    )
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
        permissionList.add(Manifest.permission.BLUETOOTH)
        permissionList.add(Manifest.permission.BLUETOOTH_ADMIN)
    }
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
        permissionList.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissionList.add(Manifest.permission.BLUETOOTH_SCAN)
        permissionList.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        permissionList.add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissionList.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        permissionList.add(Manifest.permission.POST_NOTIFICATIONS)
    }

    val permissionsState = rememberMultiplePermissionsState(permissions = permissionList)

    if (permissionsState.allPermissionsGranted) {
        val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(LocalContext.current))
        val myName by viewModel.myName.collectAsState()
        
        var isRegistered by remember { mutableStateOf(myName.isNotBlank() && !myName.startsWith("Node-")) }
        
        AnimatedContent(
            targetState = isRegistered,
            transitionSpec = {
                (fadeIn(animationSpec = tween(500)) + slideInVertically { height -> height }) togetherWith
                fadeOut(animationSpec = tween(500))
            }, label = "registration_transition"
        ) { registered ->
            if (!registered) {
                RegistrationScreen(
                    onSkip = { isRegistered = true },
                    onRegister = { newName ->
                        viewModel.updateName(newName)
                        isRegistered = true
                    }
                )
            } else {
                MainScreen(viewModel, themeManager, sharedUri)
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "SYSTEM AUTHORIZATION REQUIRED",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "MeshNode requires Location, Bluetooth, and Wi-Fi Direct permissions to establish decentralized connections.",
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 32.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Button(
                onClick = { permissionsState.launchMultiplePermissionRequest() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("INITIATE AUTHORIZATION", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RegistrationScreen(onSkip: () -> Unit, onRegister: (String) -> Unit) {
    var nameInput by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "CHOOSE YOUR IDENTITY",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            placeholder = { Text("Enter device name...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = {
                if (nameInput.isNotBlank()) onRegister(nameInput)
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        ) {
            Text("SET IDENTITY", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onSkip) {
            Text("Skip (Use Auto-Assigned Name)", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainScreen(viewModel: ChatViewModel, themeManager: ThemeManager, sharedUri: android.net.Uri? = null) {
    val navController = rememberNavController()
    val myName by viewModel.myName.collectAsState()
    
    DisposableEffect(Unit) {
        viewModel.startMesh()
        onDispose { viewModel.stopMesh() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 24.dp)
                        .statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "MESH::NODE",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "LINK ACTIVE (BT + WIFI)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(myName.uppercase(), color = MaterialTheme.colorScheme.secondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            }
        },
        bottomBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                BottomNavBar(navController)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            NavHost(
                navController = navController, 
                startDestination = if (sharedUri != null) "files" else "network",
                enterTransition = { fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f) },
                exitTransition = { fadeOut(animationSpec = tween(300)) + scaleOut(targetScale = 1.05f) }
            ) {
                composable("network") { NetworkScreen(viewModel, viewModel(factory = BleViewModel.Factory(LocalContext.current)), navController) }
                composable("messages/{targetNodeId}") { backStackEntry -> 
                    val targetId = backStackEntry.arguments?.getString("targetNodeId") ?: "GLOBAL"
                    ChatScreenBody(viewModel, targetId) 
                }
                composable("groups") { GroupsScreen(viewModel, navController) }
                composable("files") { FilesActionScreen(viewModel, navController, sharedUri) }
                composable("config") { ConfigScreen(viewModel, themeManager) }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GroupsScreen(viewModel: ChatViewModel, navController: NavHostController) {
    var newGroupName by remember { mutableStateOf("") }
    val groups by viewModel.groups.collectAsState()
    
    val allGroups = groups.map { it.groupId }.distinct()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)) {
        Text("GROUP COMMS", fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text("Discover or create multi-node channels", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("JOIN / CREATE GROUP", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newGroupName,
                onValueChange = { newGroupName = it.uppercase().replace(" ", "_") },
                placeholder = { Text("Group Name...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(modifier = Modifier.width(16.dp))
            FloatingActionButton(
                onClick = { 
                    if (newGroupName.isNotBlank()) {
                        viewModel.joinGroup(newGroupName)
                        navController.navigate("messages/GROUP_$newGroupName") 
                        newGroupName = ""
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Join")
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("ACTIVE CHANNELS", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(allGroups, key = { it }) { group ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                        .clickable { navController.navigate("messages/$group") }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Group, contentDescription = "Group", tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(group.removePrefix("GROUP_"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .height(80.dp)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavBarItem(
            icon = Icons.Filled.Hub,
            label = "NET",
            selected = currentRoute == "network",
            onClick = { navController.navigate("network") { launchSingleTop = true; restoreState = true } }
        )
        NavBarItem(
            icon = Icons.Filled.Folder,
            label = "FILES",
            selected = currentRoute == "files",
            onClick = { navController.navigate("files") { launchSingleTop = true; restoreState = true } }
        )
        NavBarItem(
            icon = Icons.Filled.Public,
            label = "GLOBAL",
            selected = currentRoute == "messages/GLOBAL",
            onClick = { navController.navigate("messages/GLOBAL") { launchSingleTop = true; restoreState = true } }
        )
        NavBarItem(
            icon = Icons.Filled.Groups,
            label = "GROUPS",
            selected = currentRoute == "groups" || currentRoute?.startsWith("messages/GROUP_") == true,
            onClick = { navController.navigate("groups") { launchSingleTop = true; restoreState = true } }
        )
        NavBarItem(
            icon = Icons.Filled.Settings,
            label = "CONFIG",
            selected = currentRoute == "config",
            onClick = { navController.navigate("config") { launchSingleTop = true; restoreState = true } }
        )
    }
}

@Composable
fun NavBarItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), label = "color")
    val boxAlpha by animateFloatAsState(if (selected) 0.2f else 0f, label = "alpha")
    val yOffset by animateDpAsState(if (selected) (-4).dp else 0.dp, label = "yOffset")

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
            .offset(y = yOffset),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary.copy(alpha = boxAlpha), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Icon(icon, contentDescription = label, tint = color)
        }
        Text(
            text = label, 
            fontSize = 9.sp, 
            fontWeight = FontWeight.Bold, 
            color = color,
            modifier = Modifier.padding(top = 4.dp),
            letterSpacing = 1.sp
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatScreenBody(viewModel: ChatViewModel, targetNodeId: String) {
    val messages by viewModel.messages.collectAsState()
    val peers by viewModel.peers.collectAsState()
    var inputText by remember { mutableStateOf("") }
    
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.sendFile(uri, targetNodeId)
        }
    }

    val chatMessages = messages.filter {
        if (targetNodeId == "GLOBAL") {
            it.targetNodeId == "GLOBAL"
        } else if (targetNodeId.startsWith("GROUP_")) {
            it.targetNodeId == targetNodeId
        } else {
            (it.targetNodeId == targetNodeId && it.senderNodeId == viewModel.myNodeId) ||
            (it.targetNodeId == viewModel.myNodeId && it.senderNodeId == targetNodeId)
        }
    }

    val targetName = if (targetNodeId == "GLOBAL") "Global Mesh Chat" 
        else if (targetNodeId.startsWith("GROUP_")) targetNodeId.removePrefix("GROUP_") + " Group"
        else peers.values.find { it.nodeId == targetNodeId }?.name ?: "Unknown Node"

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (targetNodeId == "GLOBAL") Icons.Filled.Public else if(targetNodeId.startsWith("GROUP_")) Icons.Filled.People else Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(targetName.uppercase(), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black, fontSize = 16.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                
                if (targetNodeId.startsWith("GROUP_") && peers.isNotEmpty()) {
                    var showDialog by remember { mutableStateOf(false) }
                    IconButton(onClick = { showDialog = true }, modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).size(36.dp)) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "Add users", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    if (showDialog) {
                        AlertDialog(
                            onDismissRequest = { showDialog = false },
                            title = { Text("Add participants") },
                            text = {
                                LazyColumn {
                                    items(peers.values.toList(), key = { it.nodeId }) { peer ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                viewModel.sendInvite(targetNodeId, peer.nodeId)
                                                showDialog = false
                                            }.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Person, contentDescription = null)
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text(peer.name)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showDialog = false }) { Text("Close") }
                            }
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            reverseLayout = true
        ) {
            items(chatMessages.reversed(), key = { it.id }) { msg ->
                MessageBubble(
                    message = msg, 
                    onDelete = { viewModel.deleteMessage(msg.id, targetNodeId) },
                    modifier = Modifier.animateItem()
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { fileLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)) {
                Icon(Icons.Filled.AttachFile, contentDescription = "Attach File", tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Transmit data...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f), fontSize = 14.sp) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedTextColor = MaterialTheme.colorScheme.onBackground,
                    unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            FloatingActionButton(
                onClick = {
                    viewModel.sendMessage(inputText, targetNodeId)
                    inputText = ""
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: Message, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val alignment = if (message.isMe) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
    val outlineColor = if (message.isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
    val textColor = MaterialTheme.colorScheme.onSurface
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    var showDropdown by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = alignment
        ) {
            if (!message.isMe) {
                Text(
                    text = message.senderName.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                    letterSpacing = 1.sp
                )
            }
            Box {
                Box(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { showDropdown = true }
                        )
                        .background(bubbleColor, RoundedCornerShape(20.dp, 20.dp, if (message.isMe) 4.dp else 20.dp, if (message.isMe) 20.dp else 4.dp))
                        .border(1.dp, outlineColor, RoundedCornerShape(20.dp, 20.dp, if (message.isMe) 4.dp else 20.dp, if (message.isMe) 20.dp else 4.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = message.text, 
                            color = textColor,
                            fontSize = 15.sp,
                            lineHeight = 20.sp
                        )
                        if (message.fileUri != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val context = LocalContext.current
                        Button(
                            onClick = {
                                try {
                                    val uri = Uri.parse(message.fileUri)
                                    var mimeType = context.contentResolver.getType(uri)
                                    if (mimeType == null || mimeType == "*/*") {
                                        val extension = message.text.substringAfterLast('.', "")
                                        if (extension.isNotBlank()) {
                                            mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
                                        }
                                    }
                                    if (mimeType == null) mimeType = "*/*"
                                    
                                    if (mimeType == "application/vnd.android.package-archive" && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        if (!context.packageManager.canRequestPackageInstalls()) {
                                            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                                            android.widget.Toast.makeText(context, "Please allow installing unknown apps.", android.widget.Toast.LENGTH_LONG).show()
                                            return@Button
                                        }
                                    }
                                    
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, mimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Icon(Icons.Filled.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("OPEN FILE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                } // Closes Column
                } // Closes Box(modifier=...)
                DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showDropdown = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NetworkScreen(viewModel: ChatViewModel, bleViewModel: com.example.viewmodel.BleViewModel, navController: NavHostController) {
    val peers by viewModel.peers.collectAsState()
    val myName by viewModel.myName.collectAsState()
    
    val scannedDevices by bleViewModel.scannedDevices.collectAsState()
    var mode by remember { mutableStateOf("wifi") }
    
    DisposableEffect(mode) {
        if (mode == "proximity") {
            bleViewModel.startScanning(viewModel.myNodeId, myName)
        } else {
            bleViewModel.stopScanning()
        }
        onDispose {
            bleViewModel.stopScanning()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "pulse_anim"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { mode = "wifi" },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (mode == "wifi") MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, contentColor = if (mode == "wifi") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text("Wi-Fi API", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { mode = "proximity" },
                modifier = Modifier.weight(1f).height(40.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (mode == "proximity") MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, contentColor = if (mode == "proximity") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text("Proximity", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (mode == "wifi") {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Neon radar rings
                Box(modifier = Modifier.size(280.dp).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape))
                Box(modifier = Modifier.size(200.dp).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape))
                Box(modifier = Modifier.size(120.dp).border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape))
                
                // Central node glow
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.zIndex(10f)) {
                    Box(
                        modifier = Modifier.size(72.dp).background(
                            androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            ), CircleShape
                        ).scale(scale).border(2.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = "Me", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(36.dp))
                    }
                    Text(myName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))
                }

                peers.values.toList().take(3).forEachIndexed { index, peer ->
                    val xOffset = if (index == 0) 90.dp else if (index == 1) (-75).dp else 80.dp
                    val yOffset = if (index == 0) (-80).dp else if (index == 1) 90.dp else 110.dp
                    val colorPrimary = if (peer.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    val label = if (peer.isConnected) "DIRECT LINK" else "DISCOVERING..."
                    
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset(xOffset, yOffset).clickable { navController.navigate("messages/${peer.nodeId}") }
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surface, CircleShape).border(2.dp, colorPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Smartphone, contentDescription = "Peer", tint = colorPrimary, modifier = Modifier.size(24.dp))
                        }
                        Text(
                            label,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(top = 6.dp).background(colorPrimary, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth().weight(0.9f),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                shadowElevation = 24.dp
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("AVAILABLE NODES ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("[${peers.size}]", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { viewModel.restartScanning() },
                            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Rescan", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                    
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (peers.isEmpty()) {
                            item {
                                Text("Awaiting mesh connections...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.6f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                        }
                        items(peers.values.toList(), key = { it.nodeId }) { peer ->
                            val isConnected = peer.isConnected
                            val tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                                    .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                    .clickable { navController.navigate("messages/${peer.nodeId}") }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(48.dp).background(tint.copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Router, contentDescription = "Node", tint = tint)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(peer.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Ping: <1ms", fontSize = 10.sp, color = tint)
                                    }
                                    Text(if (isConnected) "Active Connection" else "Connecting...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Proximity Mode
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Box(modifier = Modifier.fillMaxWidth().weight(0.4f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("PROXIMITY RADAR", fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Discover BLE devices without Wi-Fi.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().weight(0.6f),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    shadowElevation = 24.dp
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("NEARBY DEVICES ", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("[${scannedDevices.size}]", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                        }

                        if (scannedDevices.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Scanning BLE signals...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(scannedDevices) { device ->
                                    val distanceEst = when {
                                        device.rssi > -60 -> "Very Close"
                                        device.rssi > -80 -> "Nearby"
                                        else -> "Far"
                                    }
                                    val distanceColor = when {
                                        device.rssi > -60 -> MaterialTheme.colorScheme.primary
                                        device.rssi > -80 -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.size(40.dp).background(distanceColor.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Filled.Bluetooth, contentDescription = null, tint = distanceColor)
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(device.name ?: "Unknown", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                            Text(device.address, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("${device.rssi} dBm", fontWeight = FontWeight.Bold, color = distanceColor)
                                            Text(distanceEst, fontSize = 10.sp, color = distanceColor.copy(alpha = 0.8f))
                                            if (device.nodeId != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Button(
                                                    onClick = { navController.navigate("messages/${device.nodeId}") },
                                                    modifier = Modifier.height(28.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                                ) {
                                                    Text("Chat", fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(viewModel: ChatViewModel, themeManager: ThemeManager) {
    val myName by viewModel.myName.collectAsState()
    var nameInput by remember { mutableStateOf(myName) }
    val currentTheme by themeManager.currentTheme.collectAsState()
    var themeDropdownExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("SYSTEM CONFIG", fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text("Modify node parameters", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("NODE IDENTITY ALIAS", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { viewModel.updateName(nameInput) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        ) {
            Text("UPDATE IDENTITY & REBOOT MESH", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("APP THEME", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        androidx.compose.material3.OutlinedButton(
            onClick = { themeDropdownExpanded = true },
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(currentTheme.displayName, color = MaterialTheme.colorScheme.onBackground)
        }
        DropdownMenu(
            expanded = themeDropdownExpanded,
            onDismissRequest = { themeDropdownExpanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            com.example.ui.theme.AppTheme.values().forEach { themeOption ->
                DropdownMenuItem(
                    text = { Text(themeOption.displayName, color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        themeManager.setTheme(themeOption)
                        themeDropdownExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun FilesActionScreen(viewModel: ChatViewModel, navController: NavHostController, sharedUri: android.net.Uri? = null) {
    var selectedUri by remember(sharedUri) { mutableStateOf(sharedUri) }
    
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
        }
    }
    
    val peers by viewModel.peers.collectAsState()
    val groups by viewModel.groups.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(96.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("SECURE FILE TRANSFER", fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Transmit a file payload securely over the decentralized Mesh Network.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 14.sp)
        Spacer(modifier = Modifier.height(48.dp))
        
        if (selectedUri == null) {
            Button(
                onClick = { fileLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("SELECT PAYLOAD", fontWeight = FontWeight.Bold)
            }
        } else {
            Text("File Selected! Choose destination:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))

            // GLOBAL
            Button(
                onClick = { 
                    viewModel.sendFile(selectedUri!!, "GLOBAL")
                    navController.navigate("messages/GLOBAL")
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
            ) {
                Text("SEND TO GLOBAL CHAT", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // PEERS
            if (peers.isNotEmpty()) {
                Text("NEAREST USERS", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                peers.values.forEach { peer ->
                    Button(
                        onClick = {
                            viewModel.sendFile(selectedUri!!, peer.nodeId)
                            navController.navigate("messages/${peer.nodeId}")
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("Send to ${peer.name}")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // GROUPS
            if (groups.isNotEmpty()) {
                Text("GROUPS", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                groups.forEach { group ->
                    Button(
                        onClick = {
                            viewModel.sendFile(selectedUri!!, group.groupId)
                            navController.navigate("messages/${group.groupId}")
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("Send to ${group.name}")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { selectedUri = null },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("CANCEL / RESELECT")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}


