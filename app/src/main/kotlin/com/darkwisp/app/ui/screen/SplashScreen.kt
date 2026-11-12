package com.darkwisp.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import com.darkwisp.app.R
import com.darkwisp.app.ui.component.PostillaBrandMark
import com.darkwisp.app.ui.component.PostillaWordmark
import com.darkwisp.app.ui.component.TorCornerButton
import com.darkwisp.app.viewmodel.LiveMetrics
import com.darkwisp.app.viewmodel.SplashViewModel

@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onSignUp: () -> Unit,
    onLogIn: () -> Unit,
    onToggleTor: (Boolean) -> Unit = {},
    onCancel: (() -> Unit)? = null
) {
    val liveMetrics by viewModel.liveMetrics.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (onCancel != null) {
            Text(
                text = stringResource(R.string.btn_cancel),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 16.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PostillaBrandMark(size = 92.dp)
            Spacer(Modifier.height(18.dp))
            PostillaWordmark(fontSize = 52.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.auth_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            liveMetrics?.let { OnlineCard(it) }

            Spacer(Modifier.height(40.dp))

            Button(
                onClick = onSignUp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.splash_create_account))
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onLogIn,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.splash_log_in))
            }
        }

        TorCornerButton(
            onToggle = onToggleTor,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
        )
    }
}

@Composable
private fun OnlineCard(metrics: LiveMetrics) {
    Spacer(Modifier.height(18.dp))
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.splash_people_online, metrics.online),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
