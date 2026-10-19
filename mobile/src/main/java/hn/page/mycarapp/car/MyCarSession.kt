package hn.page.mycarapp.car

import androidx.car.app.Screen
import androidx.car.app.Session

class MyCarSession : Session() {
    override fun onCreateScreen(intent: android.content.Intent): Screen {
        return MainCarScreen(carContext)
    }
}
