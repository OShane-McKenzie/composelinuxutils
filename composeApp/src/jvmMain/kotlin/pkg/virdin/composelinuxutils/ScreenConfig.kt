package pkg.virdin.composelinuxutils

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

object ScreenConfig {
    enum class Orientation{
        PORTRAIT, LANDSCAPE
    }
    val screenHeight = mutableStateOf(768.dp)
    val screenWidth = mutableStateOf(1366.dp)
    val screenRatio = mutableStateOf(1.0f)

    fun getScreenOrientation():Orientation{
        return if (screenRatio.value >= 1f) Orientation.LANDSCAPE else Orientation.PORTRAIT
    }
    fun isPortrait():Boolean{
        return getScreenOrientation() == Orientation.PORTRAIT
    }
    fun isLandscape():Boolean{
        return getScreenOrientation() == Orientation.LANDSCAPE
    }

    fun widthByOrientation(portrait: Float, landscape:Float): Dp {
        return when(getScreenOrientation()){
            Orientation.LANDSCAPE -> screenWidth.value*landscape
            Orientation.PORTRAIT -> screenWidth.value*portrait
        }
    }

    fun heightByOrientation(portrait: Float, landscape:Float): Dp {
        return when(getScreenOrientation()){
            Orientation.LANDSCAPE -> screenHeight.value*landscape
            Orientation.PORTRAIT -> screenHeight.value*portrait
        }
    }

    fun dpIfOrientation(landscape: Dp, portrait: Dp): Dp {
        return when(getScreenOrientation()){
            Orientation.LANDSCAPE -> landscape
            Orientation.PORTRAIT -> portrait
        }
    }

    fun spIfOrientation(landscape: TextUnit, portrait: TextUnit): TextUnit {
        return when(getScreenOrientation()){

            Orientation.LANDSCAPE -> landscape
            Orientation.PORTRAIT -> portrait
        }
    }

    fun getSystemSize(coordinates: LayoutCoordinates, density: Density){

        with(density){
            screenHeight.value = coordinates.size.height.toDp()
            screenWidth.value = coordinates.size.width.toDp()
            screenRatio.value = (coordinates.size.width.toDp()/coordinates.size.height.toDp())
        }
    }
}