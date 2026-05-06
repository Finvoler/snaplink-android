package com.example.r2imagebed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.r2imagebed.AppViewModel
import com.example.r2imagebed.ui.screens.ConfigScreen
import com.example.r2imagebed.ui.screens.FilesScreen
import com.example.r2imagebed.ui.screens.UploadScreen
import com.example.r2imagebed.ui.theme.SlateBlue
import com.example.r2imagebed.ui.theme.SoftWhite

@Composable
fun R2ImageBedApp(viewModel: AppViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val destinations = listOf(
        AppDestination("config", "配置", Icons.Default.Settings),
        AppDestination("upload", "上传", Icons.Default.CloudUpload),
        AppDestination("files", "文件", Icons.Default.Folder)
    )
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.bannerMessage) {
        val message = viewModel.bannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissBanner()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            val currentDestination = navController.currentBackStackEntryAsState().value?.destination
            NavigationBar(
                containerColor = SoftWhite.copy(alpha = 0.94f)
            ) {
                destinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (destination.route == "upload" && viewModel.isPhotoPickerOpen) {
                                viewModel.submitPhotoPickerSelection(context)
                                return@NavigationBarItem
                            }
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SoftWhite,
                            selectedTextColor = SlateBlue,
                            indicatorColor = SlateBlue,
                            unselectedIconColor = SlateBlue,
                            unselectedTextColor = SlateBlue
                        ),
                        icon = {
                            Box {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label
                                )
                                if (destination.route == "files" && viewModel.listing.objects.isNotEmpty()) {
                                    Badge(modifier = Modifier.padding(start = 16.dp)) {
                                        Text(text = viewModel.listing.objects.size.toString())
                                    }
                                }
                                if (destination.route == "upload" && viewModel.isPhotoPickerOpen && viewModel.pendingPhotoPickerUris.isNotEmpty()) {
                                    Badge(modifier = Modifier.padding(start = 16.dp)) {
                                        Text(text = viewModel.pendingPhotoPickerUris.size.toString())
                                    }
                                }
                            }
                        },
                        label = { Text(text = destination.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            SoftWhite,
                            SoftWhite
                        )
                    )
                )
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp))
        ) {
            NavHost(
                navController = navController,
                startDestination = "upload"
            ) {
                composable("config") {
                    ConfigScreen(viewModel = viewModel)
                }
                composable("upload") {
                    UploadScreen(viewModel = viewModel)
                }
                composable("files") {
                    FilesScreen(viewModel = viewModel)
                }
            }
        }
    }
}

private data class AppDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)