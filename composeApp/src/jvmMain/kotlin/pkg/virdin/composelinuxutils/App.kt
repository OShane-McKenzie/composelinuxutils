package pkg.virdin.composelinuxutils

//import androidx.compose.animation.AnimatedVisibility
//import androidx.compose.foundation.Image
//import androidx.compose.foundation.background
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.Arrangement
//import androidx.compose.foundation.layout.Column
//import androidx.compose.foundation.layout.FlowRow
//import androidx.compose.foundation.layout.PaddingValues
//import androidx.compose.foundation.layout.Row
//import androidx.compose.foundation.layout.Spacer
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.fillMaxWidth
//import androidx.compose.foundation.layout.height
//import androidx.compose.foundation.layout.padding
//import androidx.compose.foundation.layout.safeContentPadding
//import androidx.compose.foundation.layout.size
//import androidx.compose.foundation.layout.width
//import androidx.compose.foundation.lazy.grid.GridCells
//import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
//import androidx.compose.foundation.lazy.grid.items
//import androidx.compose.foundation.shape.CircleShape
//import androidx.compose.material.ModalDrawer
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.Apps
//import androidx.compose.material3.Badge
//import androidx.compose.material3.Button
//import androidx.compose.material3.Icon
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Text
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.draw.clip
//import androidx.compose.ui.text.style.TextAlign
//import androidx.compose.ui.text.style.TextOverflow
//import androidx.compose.ui.tooling.preview.Preview
//import androidx.compose.ui.unit.dp
//import org.jetbrains.compose.resources.painterResource
//
//import composelinuxutils.composeapp.generated.resources.Res
//import composelinuxutils.composeapp.generated.resources.compose_multiplatform
//import kotlinx.coroutines.launch
//
//@Composable
//@Preview
//fun App() {
//    MaterialTheme {
//        val resolver = remember { XdgIconResolver() }
//        val provider = remember { InstalledAppsProvider() }
//        val apps = remember { provider.getApps() }
//        val runner = remember { LinuxRunner() }
//        val scope = rememberCoroutineScope()
//
//        @Composable
//        fun AppItem(app: DesktopApp) {
//            Column(
//                horizontalAlignment = Alignment.CenterHorizontally,
//                modifier = Modifier.width(96.dp).clickable{
//                    scope.launch {
//                        runner.launch(app)
//                    }
//                }
//            ) {
//                SystemIcon(iconValue = app.icon ?: "application-x-executable", size = 48.dp)
//                Spacer(modifier = Modifier.height(4.dp))
//                Text(
//                    text = app.name,
//                    textAlign = TextAlign.Center,
//                    maxLines = 2,
//                    overflow = TextOverflow.Ellipsis,
//                    style = MaterialTheme.typography.labelSmall
//                )
//                if (app.isCli) Badge { Text("CLI") }
//            }
//        }
//
//        CompositionLocalProvider(LocalIconResolver provides resolver) {
//            LazyVerticalGrid(
//                columns = GridCells.Adaptive(minSize = 96.dp),
//                contentPadding = PaddingValues(16.dp),
//                horizontalArrangement = Arrangement.spacedBy(12.dp),
//                verticalArrangement = Arrangement.spacedBy(12.dp)
//            ) {
//                items(apps) { app ->
//                    AppItem(app)
//                }
//            }
//        }
//    }
//}