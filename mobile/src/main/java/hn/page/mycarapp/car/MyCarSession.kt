package hn.page.mycarapp.car

import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.annotations.ExperimentalCarApi

class MyCarSession : Session() {
    @ExperimentalCarApi
    override fun onCreateScreen(intent: android.content.Intent): Screen {
        return MainCarScreen(carContext)
    }
}
