package xyz.blockthem.mars.manager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import xyz.blockthem.mars.manager.ui.MarsNavHost
import xyz.blockthem.mars.manager.ui.theme.MarsTheme

class MainActivity : ComponentActivity() {
    private val auth get() = (application as MarsApp).auth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        auth.attach(this)
        lifecycleScope.launch { auth.restore() }
        setContent {
            MarsTheme {
                MarsNavHost(auth)
            }
        }
    }

    override fun onDestroy() {
        auth.detach(this)
        super.onDestroy()
    }
}
