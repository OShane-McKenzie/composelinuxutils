package pkg.virdin.composelinuxutils

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import pkg.virdin.composelinuxutils.json.Vson

fun main() = application {
    val runner = LinuxRunner()
    val scope = rememberCoroutineScope()

    Window(onCloseRequest = ::exitApplication) {
        LaunchedEffect(Unit) {
            val res = runner.diskUsage().output
            println(res)
        }
    }
}