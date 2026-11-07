package app.chinae.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import app.chinae.service.ChinaeService
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // shortcut: 앱을 열 때 서비스 시작. 시동 시 자동 시작(부팅 수신)은 시연 흐름 확정 후 추가
        ChinaeService.start(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) { Text("친애") }
                }
            }
        }
    }
}
