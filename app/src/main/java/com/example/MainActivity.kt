package com.example

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.viewmodel.BleViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
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
        val context = LocalContext.current
        val locationManager = remember { context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager }
        var isLocationEnabled by remember { mutableStateOf(locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) }
        
        DisposableEffect(Unit) {
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == android.location.LocationManager.PROVIDERS_CHANGED_ACTION) {
                        isLocationEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                    }
                }
            }
            context.registerReceiver(receiver, android.content.IntentFilter(android.location.LocationManager.PROVIDERS_CHANGED_ACTION))
            onDispose { context.unregisterReceiver(receiver) }
        }

        if (!isLocationEnabled) {
            Column(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.LocationOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "LOCATION SERVICES DISABLED",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Due to Android restrictions, discovering nearby devices over Bluetooth & Wi-Fi requires Location services.",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 32.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("ENABLE LOCATION", fontWeight = FontWeight.Bold)
                }
            }
        } else {
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
                        onRegister = { newName, newAvatar ->
                            viewModel.updateName(newName, newAvatar)
                            isRegistered = true
                        }
                    )
                } else {
                    MainScreen(viewModel, themeManager, sharedUri)
                }
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
fun RegistrationScreen(onSkip: () -> Unit, onRegister: (String, String) -> Unit) {
    var nameInput by remember { mutableStateOf("") }
    var avatarInput by remember { mutableStateOf("👤") }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.size(96.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).border(4.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            BasicTextField(
                value = avatarInput,
                onValueChange = { if (it.length <= 2) avatarInput = it },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 48.sp, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
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
                if (nameInput.isNotBlank()) onRegister(nameInput, avatarInput)
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
                composable("network") { NetworkScreen(viewModel, navController) }
                composable("games") { com.example.ui.GamesLobbyScreen(viewModel, navController) }
                composable("game_active/{gameId}/{mode}/{peerId}/{difficulty}") { entry ->
                    val gameId = entry.arguments?.getString("gameId") ?: "dino"
                    val mode = entry.arguments?.getString("mode") ?: "single"
                    val peerId = entry.arguments?.getString("peerId") ?: "NONE"
                    val diff = entry.arguments?.getString("difficulty") ?: "medium"
                    com.example.ui.ActiveGameScreen(viewModel, navController, gameId, mode, peerId, diff)
                }
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
            icon = Icons.Filled.SportsEsports,
            label = "GAMES",
            selected = currentRoute == "games",
            onClick = { navController.navigate("games") { launchSingleTop = true; restoreState = true } }
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
    
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris != null && uris.isNotEmpty()) {
            if (uris.size == 1) {
                viewModel.sendFile(uris.first(), targetNodeId)
            } else {
                viewModel.sendFilesAsZip(uris, targetNodeId)
            }
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
                val avatar = if (msg.isMe) "👤" else peers[msg.senderNodeId]?.avatar ?: "👤"
                MessageBubble(
                    message = msg, 
                    avatar = avatar,
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
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText, targetNodeId)
                        inputText = ""
                    }
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
fun MessageBubble(message: Message, avatar: String, onDelete: () -> Unit, modifier: Modifier = Modifier) {
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
                    modifier = Modifier.padding(start = 52.dp, bottom = 4.dp),
                    letterSpacing = 1.sp
                )
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = if (message.isMe) Arrangement.End else Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!message.isMe) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha=0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(avatar, fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
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
                            .widthIn(max = 280.dp)
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
                        } // Closes Button
                        } // Closes if (message.fileUri != null)
                    } // Closes Column
                } // Closes inner Box
                DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            showDropdown = false
                            onDelete()
                        }
                    )
                }
            } // end outer box
            } // end row
        } // end column
    } // end animated visibility
} // end MessageBubble

@Composable
fun NetworkScreen(viewModel: ChatViewModel, navController: NavHostController) {
    val peers by viewModel.peers.collectAsState()
    val myName by viewModel.myName.collectAsState()
    val myAvatar by viewModel.myAvatar.collectAsState()
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(animation = tween(2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "pulse_anim"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Net and Radar Visual (Minimalist, Color-coordinated)
            val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cw = size.width / 2
                val ch = size.height / 2
                
                // Draw a subtle net in the background
                val gridSize = 60f
                val dotRadius = 2f
                for (x in 0..size.width.toInt() step gridSize.toInt()) {
                    for (y in 0..size.height.toInt() step gridSize.toInt()) {
                        drawCircle(primaryColor.copy(alpha = 0.1f), dotRadius, Offset(x.toFloat(), y.toFloat()))
                    }
                }
                
                // Connecting lines for net
                for (x in 0..size.width.toInt() step gridSize.toInt()) {
                    drawLine(primaryColor.copy(alpha = 0.05f), Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), 1f)
                }
                for (y in 0..size.height.toInt() step gridSize.toInt()) {
                    drawLine(primaryColor.copy(alpha = 0.05f), Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), 1f)
                }
            }
            
            // Neon radar rings
            Box(modifier = Modifier.size(280.dp).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape))
            Box(modifier = Modifier.size(200.dp).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape))
            Box(modifier = Modifier.size(120.dp).border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape))
            
            // Central node glow
            Box(
                modifier = Modifier.size(80.dp).background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary.copy(alpha=0.5f), MaterialTheme.colorScheme.secondary.copy(alpha=0.2f))
                    ), CircleShape
                ).scale(scale).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape).zIndex(10f),
                contentAlignment = Alignment.Center
            ) {
                Text(myAvatar, fontSize = 36.sp)
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.zIndex(10f).offset(y = 70.dp)) {
                Text(myName, fontSize = 14.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                Text("Nexus Hub", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 4.dp))
            }

            peers.values.toList().take(3).forEachIndexed { index, peer ->
                val xOffset = if (index == 0) 90.dp else if (index == 1) (-75).dp else 80.dp
                val yOffset = if (index == 0) (-80).dp else if (index == 1) 90.dp else 110.dp
                val colorPrimary = if (peer.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                val label = if (peer.isConnected) "ESTABLISHED" else "VERIFYING..."
                
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.offset(xOffset, yOffset).clickable { navController.navigate("messages/${peer.nodeId}") }
                        ) {
                            Box(
                                modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.surface, CircleShape).border(3.dp, colorPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(peer.avatar, fontSize = 28.sp)
                            }
                    Text(
                        label,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 6.dp).background(colorPrimary.copy(alpha=0.2f), RoundedCornerShape(4.dp)).border(1.dp, colorPrimary.copy(alpha=0.5f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { navController.navigate("groups") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.height(36.dp).padding(end = 8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Filled.Group, contentDescription = "Groups", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Groups", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(
                            onClick = { viewModel.restartScanning() },
                            modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Rescan", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
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
                                Text(peer.avatar, fontSize = 24.sp)
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
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(viewModel: ChatViewModel, themeManager: ThemeManager) {
    val myName by viewModel.myName.collectAsState()
    val myAvatar by viewModel.myAvatar.collectAsState()
    var nameInput by remember { mutableStateOf(myName) }
    var avatarInput by remember { mutableStateOf(myAvatar) }
    val currentTheme by themeManager.currentTheme.collectAsState()
    var themeDropdownExpanded by remember { mutableStateOf(false) }
    var aggressiveDiscovery by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("SYSTEM CONFIG", fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text("Modify node parameters", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(32.dp))
        
        // Identity Tile
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("NODE IDENTITY & AVATAR", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary.copy(alpha=0.2f), CircleShape).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                        BasicTextField(
                            value = avatarInput,
                            onValueChange = { if (it.length <= 2) avatarInput = it },
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 28.sp, textAlign = TextAlign.Center),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.background,
                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent,
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = { viewModel.updateName(nameInput, avatarInput) },
                        modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).size(48.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
        
        // Network Mode Tile
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Radar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Aggressive Discovery", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Scans more frequently but uses more battery", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.7f), fontSize = 10.sp)
                    }
                }
                androidx.compose.material3.Switch(
                    checked = aggressiveDiscovery,
                    onCheckedChange = { aggressiveDiscovery = it },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha=0.3f))
                )
            }
        }

                // Theme Tile
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ColorLens, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("APP THEME", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(com.example.ui.theme.AppTheme.values()) { themeOption ->
                        val isSelected = currentTheme == themeOption
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { themeManager.setTheme(themeOption) }) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(if (themeOption.isDark) Color(0xFF121212) else Color.White, CircleShape)
                                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha=0.3f), CircleShape)
                            ) {
                                // Provide a general hint
                                val hintColor = if (themeOption.name.contains("BLUE")) Color.Blue else if (themeOption.name.contains("RED")) Color.Red else if (themeOption.name.contains("GREEN")) Color.Green else Color.Gray
                                Box(modifier = Modifier.size(20.dp).background(hintColor, CircleShape).align(Alignment.Center))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(themeOption.displayName.substringBefore(" "), fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            }
        }
        
        // FILE TRANSFER SETTINGS TILE
        var autoCompress by remember { mutableStateOf(false) }
        var highSpeed by remember { mutableStateOf(true) }
        var showAck by remember { mutableStateOf(true) }
        val currentLang by themeManager.language.collectAsState()
        
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("LANGUAGE", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("App Language", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(if (currentLang == "en") "English" else "Русский", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { themeManager.setLanguage(if (currentLang == "en") "ru" else "en") }) {
                        Text(if (currentLang == "en") "Switch to RU" else "Switch to EN")
                    }
                }
            }
        }
        
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("FILE TRANSFER PREFERENCES", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-compress files", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Zip files silently before sending", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(checked = autoCompress, onCheckedChange = { autoCompress = it })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("High Speed Bandwidth", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Increases chunk size for faster delivery", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(checked = highSpeed, onCheckedChange = { highSpeed = it })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Acks", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Display file transfer acknowledgments", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(checked = showAck, onCheckedChange = { showAck = it })
                }
            }
        }
        
        // Notification Volume Slider
        var notificationVolume by remember { mutableStateOf(0.8f) }
        var showAdvancedNotif by remember { mutableStateOf(false) }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).animateContentSize()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { showAdvancedNotif = !showAdvancedNotif }) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("NOTIFICATION ALERTS", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                    Icon(if (showAdvancedNotif) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(visible = showAdvancedNotif) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Alert Volume", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.8f))
                        androidx.compose.material3.Slider(
                            value = notificationVolume,
                            onValueChange = { notificationVolume = it },
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        )
                        var priorityLevel by remember { mutableStateOf(0.5f) }
                        Text("Vibration Intensity", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.8f))
                        androidx.compose.material3.Slider(
                            value = priorityLevel,
                            onValueChange = { priorityLevel = it },
                            colors = androidx.compose.material3.SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            }
        }
        
        // Cache Tile
        var isCacheCleared by remember { mutableStateOf(false) }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp).clickable { isCacheCleared = true }
        ) {
            Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedVisibility(visible = !isCacheCleared) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                    AnimatedVisibility(visible = isCacheCleared) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(if (isCacheCleared) "Cache Cleared" else "Clear Mesh Cache", color = if (isCacheCleared) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (!isCacheCleared) {
                    Text("14.2 MB", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.6f), fontSize = 12.sp)
                } else {
                    Text("0.0 MB", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.6f), fontSize = 12.sp)
                }
            }
        }
    }
}

fun getFileNameAndSize(context: android.content.Context, uri: android.net.Uri): Pair<String, Long> {
    var name = "Unknown Payload"
    var size = 0L
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex != -1) name = cursor.getString(nameIndex) ?: "Unknown Payload"
                if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
            }
        }
    } catch (e: Exception) {
        name = uri.lastPathSegment ?: "Unknown Payload"
    }
    if (name.isBlank()) {
        name = uri.lastPathSegment ?: "PayloadFile"
    }
    return Pair(name, size)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    if (digitGroups >= units.size) return "$bytes B"
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun LaserDiscoveryRadar(modifier: Modifier = Modifier, isScanning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SweepRotation"
    )
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse1"
    )
    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, delayMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse2"
    )

    val radarColor = MaterialTheme.colorScheme.primary
    val ringColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    val sweepColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

    Box(
        modifier = modifier
            .size(140.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.width / 2

            drawCircle(color = ringColor, radius = maxRadius * 0.35f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
            drawCircle(color = ringColor, radius = maxRadius * 0.7f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
            drawCircle(color = ringColor, radius = maxRadius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))

            drawLine(color = ringColor, start = Offset(0f, center.y), end = Offset(size.width, center.y), strokeWidth = 1.dp.toPx())
            drawLine(color = ringColor, start = Offset(center.x, 0f), end = Offset(center.x, size.height), strokeWidth = 1.dp.toPx())

            if (isScanning) {
                drawCircle(color = ringColor.copy(alpha = (1f - pulseScale1) * 0.4f), radius = maxRadius * pulseScale1, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                drawCircle(color = ringColor.copy(alpha = (1f - pulseScale2) * 0.4f), radius = maxRadius * pulseScale2, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))

                drawArc(
                    color = sweepColor,
                    startAngle = rotation,
                    sweepAngle = -60f,
                    useCenter = true,
                    size = androidx.compose.ui.geometry.Size(size.width, size.height),
                    topLeft = Offset.Zero
                )
            }
        }
        Icon(
            imageVector = Icons.Filled.Wifi,
            contentDescription = "Beacon Active",
            tint = radarColor,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun StagedFileListItem(index: Int, uri: android.net.Uri, context: android.content.Context, selectedUris: List<android.net.Uri>, onRemove: (List<android.net.Uri>) -> Unit) {
    val (fileName, fileSize) = remember(uri) { getFileNameAndSize(context, uri) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val icon = when {
                ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg") -> Icons.Filled.Image
                ext in listOf("zip", "rar", "7z", "tar", "gz") -> Icons.Filled.Folder
                else -> Icons.Filled.InsertDriveFile
            }
            
            Icon(
                imageVector = icon,
                contentDescription = "Type",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = formatFileSize(fileSize),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            
            IconButton(
                onClick = {
                    val updated = selectedUris.toMutableList()
                    updated.removeAt(index)
                    onRemove(updated)
                },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Discard File",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun FilesActionScreen(viewModel: ChatViewModel, navController: NavHostController, sharedUri: android.net.Uri? = null) {
    var selectedUris by remember(sharedUri) { mutableStateOf<List<android.net.Uri>>(if (sharedUri != null) listOf(sharedUri) else emptyList()) }
    
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris != null && uris.isNotEmpty()) {
            selectedUris = selectedUris + uris
        }
    }
    
    val peers by viewModel.peers.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRefreshingScan by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Dynamic discovery status / Force scan trigger bar
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isRefreshingScan) MaterialTheme.colorScheme.primary else Color(0xFF4CAF50), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isRefreshingScan) "DISCOVERING PEERS..." else "ACTIVE DISCOVERY ON",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = if (isRefreshingScan) MaterialTheme.colorScheme.primary else Color(0xFF4CAF50)
                        )
                        Text(
                            text = "Local Node: ${viewModel.myNodeId.uppercase()}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                
                IconButton(
                    onClick = {
                        isRefreshingScan = true
                        viewModel.restartScanning()
                        scope.launch {
                            kotlinx.coroutines.delay(2500)
                            isRefreshingScan = false
                        }
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
                ) {
                    if (isRefreshingScan) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Trigger Device Scan",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Animated Radar Device discovery
        LaserDiscoveryRadar(isScanning = true)
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "SECURE FILE MESH TRANSMITTER",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Broadcast payloads peer-to-peer securely. Staged files are compiled instantly into encrypted transmissions.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (selectedUris.isEmpty()) {
            Button(
                onClick = { fileLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ADD FILE PAYLOAD", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
        } else {
            // Selected files header with Quick count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STAGED PAYLOADS (${selectedUris.size})",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                TextButton(
                    onClick = { selectedUris = emptyList() },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
            
            // Render Staged Files with beautiful details
            selectedUris.forEachIndexed { index, uri ->
                StagedFileListItem(
                    index = index,
                    uri = uri,
                    context = context,
                    selectedUris = selectedUris,
                    onRemove = { selectedUris = it }
                )
            }
            
            Button(
                onClick = { fileLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(40.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ADD MORE FILES", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            // Primary send action: Decent broad send
            Button(
                onClick = { 
                    if (selectedUris.size == 1) {
                        viewModel.sendFile(selectedUris.first(), "GLOBAL")
                    } else {
                        viewModel.sendFilesAsZip(selectedUris, "GLOBAL")
                    }
                    navController.navigate("messages/GLOBAL")
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SEND TO GLOBAL CHAT", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // NEARBY USERS SECTION
            if (peers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NEAREST DEVICES",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                peers.values.forEach { peer ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                if (selectedUris.size == 1) {
                                    viewModel.sendFile(selectedUris.first(), peer.nodeId)
                                } else {
                                    viewModel.sendFilesAsZip(selectedUris, peer.nodeId)
                                }
                                navController.navigate("messages/${peer.nodeId}")
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(14.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = peer.avatar, fontSize = 18.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = peer.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Node ID: ${peer.nodeId.uppercase()}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            
                            Button(
                                onClick = {
                                    if (selectedUris.size == 1) {
                                        viewModel.sendFile(selectedUris.first(), peer.nodeId)
                                    } else {
                                        viewModel.sendFilesAsZip(selectedUris, peer.nodeId)
                                    }
                                    navController.navigate("messages/${peer.nodeId}")
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Send", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Waiting for nearest peers to appear...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Or tap the refresh icon above to trigger discovery.",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            // GROUPS SECTION
            if (groups.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SECURE GROUPS",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                groups.forEach { group ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                if (selectedUris.size == 1) {
                                    viewModel.sendFile(selectedUris.first(), group.groupId)
                                } else {
                                    viewModel.sendFilesAsZip(selectedUris, group.groupId)
                                }
                                navController.navigate("messages/${group.groupId}")
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(14.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "👥", fontSize = 18.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = group.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Group ID: ${group.groupId.uppercase()}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            
                            Button(
                                onClick = {
                                    if (selectedUris.size == 1) {
                                        viewModel.sendFile(selectedUris.first(), group.groupId)
                                    } else {
                                        viewModel.sendFilesAsZip(selectedUris, group.groupId)
                                    }
                                    navController.navigate("messages/${group.groupId}")
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Send", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { selectedUris = emptyList() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("CANCEL / RESELECT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}


