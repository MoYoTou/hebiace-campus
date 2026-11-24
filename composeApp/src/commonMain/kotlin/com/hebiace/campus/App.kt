package com.hebiace.campus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hebiace.campus.ui.life.LifeScreen
import com.hebiace.campus.ui.profile.ProfileScreen
import com.hebiace.campus.ui.schedule.ScheduleScreen
import com.hebiace.campus.ui.tasks.TasksScreen
import com.hebiace.campus.ui.theme.HebiaceTheme

@Composable
fun App() {
    HebiaceTheme {
        var tab by remember { mutableStateOf(0) }
        val tabs = listOf("课程", "作业", "生活", "我的")
        Scaffold(
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .height(58.dp)
                    ) {
                        tabs.forEachIndexed { i, title ->
                            val selected = i == tab
                            Column(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { tab = i },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                            ) {
                                Text(
                                    title,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (selected) {
                                    Box(
                                        Modifier
                                            .padding(top = 3.dp)
                                            .size(4.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> ScheduleScreen()
                    1 -> TasksScreen()
                    2 -> LifeScreen()
                    3 -> ProfileScreen()
                }
            }
        }
    }
}
