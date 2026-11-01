package com.example.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ledger.data.remote.DbProvider
import com.example.ledger.ui.components.LoadingBox
import com.example.ledger.ui.navigation.LedgerNavHost
import com.example.ledger.ui.navigation.Routes
import com.example.ledger.ui.theme.LedgerTheme
import com.example.ledger.ui.theme.TextSecondary
import com.example.ledger.ui.theme.WarningAmber
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LedgerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (DbProvider.isConfigured) {
                        SessionGate()
                    } else {
                        ConfigMissingScreen()
                    }
                }
            }
        }
    }
}

/**
 * 启动落地页判断：等待本地会话加载完成后，已登录进入首页，否则进入登录页。
 * 超时兜底避免弱网下长时间停留在加载态。
 */
@Composable
private fun SessionGate() {
    var startRoute by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val container = remember { (context.applicationContext as LedgerApplication).container }

    LaunchedEffect(Unit) {
        // 先用数据库校验本地令牌是否仍有效；超时或断网时退回本地会话判断
        val validated = withTimeoutOrNull(SESSION_TIMEOUT_MS) {
            container.authRepository.validateSession()
        }
        val loggedIn = validated ?: container.authRepository.isLoggedIn()
        startRoute = if (loggedIn) Routes.HOME else Routes.AUTH
    }

    val route = startRoute
    if (route == null) {
        LoadingBox()
    } else {
        LedgerNavHost(startDestination = route)
    }
}

/** local.properties 未填写数据库连接参数时的提示页 */
@Composable
private fun ConfigMissingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = WarningAmber,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.config_missing_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.config_missing_message),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

private const val SESSION_TIMEOUT_MS = 3000L
