package tech.vasker.vector.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import tech.vasker.vector.obd.ConnectionState
import tech.vasker.vector.obd.ObdStateHolder
import tech.vasker.vector.trip.TripState
import tech.vasker.vector.ui.screen.DashboardScreen
import tech.vasker.vector.ui.screen.DiagnosticsScreen
import tech.vasker.vector.ui.screen.TripDetailScreen
import tech.vasker.vector.ui.screen.TripsScreen
import tech.vasker.vector.trip.TripDetail
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.ui.unit.dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import tech.vasker.vector.TalonApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TalonApp() {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val app = context.applicationContext as TalonApplication
    val obdHolder = remember(app) { app.obdHolder }
    val tripHolder = remember(app) { app.tripHolder }

    val connectionState by obdHolder.connectionState.collectAsState()
    val liveValues by obdHolder.liveValues.collectAsState()
    val diagnosticsData by obdHolder.diagnosticsData.collectAsState()
    val fuelBurnSession by obdHolder.fuelBurnSession.collectAsState()
    val obdLogLines by obdHolder.obdLogLines.collectAsState()
    val tripState by tripHolder.tripState.collectAsState()
    val tripSummaries by tripHolder.tripSummaries.collectAsState()

    var selectedTripId by remember { mutableStateOf<String?>(null) }
    var tripDetail by remember { mutableStateOf<TripDetail?>(null) }
    LaunchedEffect(selectedTripId) {
        tripDetail = selectedTripId?.let { tripHolder.getTripDetail(it) } ?: null
    }

    val isStale = connectionState !is ConnectionState.Connected &&
        (liveValues.speedMph != null || liveValues.rpm != null || liveValues.coolantF != null || liveValues.fuelPercent != null)
    val errorMessage = (connectionState as? ConnectionState.Error)?.message

    LaunchedEffect(Unit) {
        obdHolder.tryAutoConnect()
    }

    var showConnectSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showConnectSheet = true
    }

    fun openConnectSheet() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_CONNECT
        } else {
            @Suppress("DEPRECATION")
            Manifest.permission.BLUETOOTH
        }
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            showConnectSheet = true
        } else {
            permissionLauncher.launch(permission)
        }
    }

    if (showConnectSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConnectSheet = false },
            sheetState = sheetState,
        ) {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    adapter?.bondedDevices?.toList() ?: emptyList()
                } else emptyList()
            } else {
                @Suppress("DEPRECATION")
                adapter?.bondedDevices?.toList() ?: emptyList()
            }
            val lastName = obdHolder.getLastDeviceName()
            val hasLast = obdHolder.hasLastDevice()
            if (hasLast && lastName != null) {
                ListItem(
                    headlineContent = { Text("Reconnect to $lastName") },
                    supportingContent = { Text("Use last connected device", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.clickable {
                        obdHolder.tryAutoConnect()
                        showConnectSheet = false
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            Text(
                text = "Or choose another paired device",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (devices.isEmpty()) {
                Text(
                    text = "No paired devices",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn {
                    items(devices, key = { it.address }) { device ->
                        ListItem(
                            headlineContent = { Text(device.name ?: device.address) },
                            modifier = Modifier.clickable {
                                obdHolder.connect(device)
                                showConnectSheet = false
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    val tabs = listOf(
        Triple("Dashboard", Icons.Outlined.Speed, 0),
        Triple("Diagnostics", Icons.Outlined.Warning, 1),
        Triple("Trips", Icons.Outlined.Map, 2),
    )
    Scaffold(
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                tabs.forEach { (label, icon, index) ->
                    val selected = selectedTabIndex == index
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTabIndex = index }
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            modifier = Modifier.size(24.dp),
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        },
    ) { paddingValues ->
        when (selectedTabIndex) {
            0 -> {
                val connectMs = obdHolder.getConnectElapsedMs()
                val elapsedH = if (connectMs != null) (SystemClock.elapsedRealtime() - connectMs) / 3600000.0 else 0.0
                val gph = if (elapsedH > 0) fuelBurnSession.gallonsBurned / elapsedH else null
                DashboardScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    connectionState = connectionState,
                    liveValues = liveValues,
                    isStale = isStale,
                    errorMessage = errorMessage,
                    gallonsBurnedSinceConnect = fuelBurnSession.gallonsBurned,
                    tripDistanceMiles = if (tripState.state == TripState.ACTIVE) tripState.distanceMi else 0.0,
                    gph = gph,
                    lastDeviceName = obdHolder.getLastDeviceName(),
                    onReconnectClick = { obdHolder.tryAutoConnect() },
                    onConnectClick = { openConnectSheet() },
                    onStopTripClick = {
                        tripHolder.stopTrip(
                            userInitiated = true,
                            gallonsBurnedSinceConnect = obdHolder.fuelBurnSession.value.gallonsBurned,
                        )
                    },
                )
            }
            1 -> DiagnosticsScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                connectionState = connectionState,
                diagnosticsData = diagnosticsData,
                liveValues = liveValues,
                onReadCodes = { obdHolder.refreshDiagnostics() },
                onClearCodes = { obdHolder.clearDtc() },
                onLoadFreezeFrame = { obdHolder.loadFreezeFrame() },
                obdLogLines = obdLogLines,
                onClearObdLog = { obdHolder.clearObdLog() },
                onRunProbe = { obdHolder.runCapabilityProbe() },
                onCopyObdLog = {
                    val clip = android.content.ClipData.newPlainText("OBD Log", obdHolder.getLogTextForCopy())
                    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                },
            )
            2 -> {
                if (tripDetail != null) {
                    TripDetailScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        detail = tripDetail!!,
                        onBack = { selectedTripId = null },
                        onExport = { tripHolder.exportTrip(context, tripDetail!!.metadata.id) },
                    )
                } else {
                    TripsScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        trips = tripSummaries,
                        selectedTripId = selectedTripId,
                        onTripClick = { selectedTripId = it },
                        onExportTrip = { tripId -> tripHolder.exportTrip(context, tripId) },
                        onDeleteTrips = { ids -> tripHolder.deleteTrips(ids) },
                    )
                }
            }
        }
    }
}
