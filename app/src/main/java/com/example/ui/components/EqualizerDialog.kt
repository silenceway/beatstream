package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.PlayerController
import com.example.player.PlayerState
import com.example.ui.theme.YTRed
import com.example.ui.theme.YTSurfaceElevated

@Composable
fun EqualizerDialog(
    playerState: PlayerState,
    controller: PlayerController,
    onDismiss: () -> Unit
) {
    val bandLabels = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = YTSurfaceElevated,
        shape = RoundedCornerShape(18.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sound Equalizer",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Switch(
                    checked = playerState.equalizerEnabled,
                    onCheckedChange = { controller.setEqualizerEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = YTRed,
                        checkedTrackColor = YTRed.copy(alpha = 0.4f)
                    )
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Preset Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = if (playerState.presetNames.isNotEmpty()) {
                        playerState.presetNames
                    } else {
                        listOf("Flat", "Bass Boost", "Rock", "Pop", "Vocal", "Electronic")
                    }

                    presets.forEachIndexed { idx, presetName ->
                        FilterChip(
                            selected = playerState.currentPreset == idx,
                            onClick = {
                                controller.setEqualizerEnabled(true)
                                controller.usePreset(idx.toShort())
                            },
                            label = { Text(presetName, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedContainerColor = YTRed,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bass Boost Slider
                Text(
                    text = "Bass Boost (${playerState.bassBoostStrength / 10}%)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Slider(
                    value = playerState.bassBoostStrength.toFloat(),
                    onValueChange = {
                        controller.setEqualizerEnabled(true)
                        controller.setBassBoost(it.toInt().toShort())
                    },
                    valueRange = 0f..1000f,
                    colors = SliderDefaults.colors(
                        thumbColor = YTRed,
                        activeTrackColor = YTRed
                    ),
                    enabled = playerState.equalizerEnabled
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Frequency Bands Sliders
                Text(
                    text = "Frequency Bands",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                val displayBands = if (playerState.bandLevels.isNotEmpty()) {
                    playerState.bandLevels
                } else {
                    listOf<Short>(0, 0, 0, 0, 0)
                }

                displayBands.forEachIndexed { index, level ->
                    val label = bandLabels.getOrElse(index) { "Band $index" }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(64.dp)
                        )
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { newLvl ->
                                controller.setEqualizerEnabled(true)
                                controller.setBandLevel(index.toShort(), newLvl.toInt().toShort())
                            },
                            valueRange = -1500f..1500f,
                            colors = SliderDefaults.colors(
                                thumbColor = YTRed,
                                activeTrackColor = YTRed
                            ),
                            enabled = playerState.equalizerEnabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = YTRed)
            ) {
                Text("Done")
            }
        }
    )
}
