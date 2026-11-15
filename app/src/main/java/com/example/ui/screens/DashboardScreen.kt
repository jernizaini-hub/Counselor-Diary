package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ActivityCategory
import com.example.model.CounselingRecord
import com.example.ui.viewmodel.CloudSyncState
import com.example.ui.viewmodel.DailyTrendPoint
import com.example.ui.viewmodel.DashboardStats
import com.example.ui.viewmodel.StatsPeriod
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
  counselorName: String,
  stats: DashboardStats,
  activePeriod: StatsPeriod,
  cloudSyncState: CloudSyncState,
  recentRecords: List<CounselingRecord>,
  onPeriodSelected: (StatsPeriod) -> Unit,
  onQuickCreateCategory: (ActivityCategory) -> Unit,
  onOpenRecord: (CounselingRecord) -> Unit,
  onViewAllRecords: () -> Unit,
  onOpenSecurity: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val categoryColors = rememberCategoryColorMap()

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    // Editorial Header & Sync Status
    item {
      Column(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "Selamat Sejahtera, ${counselorName.ifBlank { "Kaunselor" }}",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CloudSyncPill(cloudSyncState = cloudSyncState, onClick = onOpenSecurity)
          Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            shape = RoundedCornerShape(50),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Filled.EnhancedEncryption,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "E2EE Aktif",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
      }
    }

    // Period Filter Chips
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        StatsPeriod.entries.forEach { period ->
          FilterChip(
            selected = activePeriod == period,
            onClick = { onPeriodSelected(period) },
            label = { Text(period.label) },
            modifier = Modifier.testTag("stats_period_${period.name.lowercase()}"),
          )
        }
      }
    }

    // 2x2 KPI Bento Grid
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          KpiBentoCard(
            title = "Jumlah Rekod",
            value = stats.totalRecords.toString(),
            subtitle = "Sesi & aktiviti",
            icon = Icons.Filled.Schedule,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
          )
          KpiBentoCard(
            title = "Sesi Kaunseling",
            value = stats.counselingSessionsCount.toString(),
            subtitle = "Individu & Kelompok",
            icon = Icons.Filled.Person,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.weight(1f),
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          KpiBentoCard(
            title = "Aktiviti Lain",
            value = stats.activitiesCount.toString(),
            subtitle = "Program, Ziarah, dll.",
            icon = Icons.Filled.Groups,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
          )
          val aiPct =
            if (stats.totalRecords > 0) (stats.aiSummaryCount * 100) / stats.totalRecords else 0
          KpiBentoCard(
            title = "Rumusan AI",
            value = "$aiPct%",
            subtitle = "${stats.totalMinutes} minit jumlah",
            icon = Icons.Filled.AutoAwesome,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    // Visual Donut Chart: Pecahan Kategori Prestasi Semasa
    item {
      Card(
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().testTag("dashboard_category_chart_card"),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text(
            text = "Analisis Pecahan Kategori",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "Taburan sesi kaunseling & aktiviti (${activePeriod.label})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(16.dp))

          if (stats.totalRecords == 0) {
            Text(
              text = "Belum ada rekod untuk tempoh ini. Tambah catatan pertama anda di bawah.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              CategoryDonutChart(
                categoryCounts = stats.categoryCounts,
                categoryColors = categoryColors,
                totalCount = stats.totalRecords,
                modifier = Modifier.size(132.dp),
              )
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
              ) {
                ActivityCategory.entries.forEach { cat ->
                  val count = stats.categoryCounts[cat] ?: 0
                  if (count > 0) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                      Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                      ) {
                        Box(
                          modifier =
                            Modifier.size(10.dp)
                              .clip(CircleShape)
                              .background(categoryColors[cat] ?: MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                          text = cat.shortLabel,
                          style = MaterialTheme.typography.bodySmall,
                          maxLines = 1,
                          overflow = TextOverflow.Ellipsis,
                        )
                      }
                      Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                      )
                    }
                  }
                }
              }
            }
          }
        }
      }
    }

    // Visual 7-Day Activity Bar Chart
    item {
      Card(
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().testTag("dashboard_trend_chart_card"),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text(
            text = "Trend Prestasi 7 Hari Terakhir",
            style = MaterialTheme.typography.titleMedium,
          )
          Text(
            text = "Kekerapan harian sesi dan aktiviti kaunselor",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(16.dp))
          WeeklyBarChart(
            points = stats.weeklyTrend,
            barColor = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // Quick Action Shortcuts
    item {
      Column {
        Text(
          text = "Catatan Pantas",
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          QuickCategoryButton("Sesi Individu", Icons.Filled.Person) {
            onQuickCreateCategory(ActivityCategory.KAUNSELING_INDIVIDU)
          }
          QuickCategoryButton("Sesi Kelompok", Icons.Filled.Groups) {
            onQuickCreateCategory(ActivityCategory.KAUNSELING_KELOMPOK)
          }
          QuickCategoryButton("Ziarah Cakna", Icons.Filled.HomeWork) {
            onQuickCreateCategory(ActivityCategory.ZIARAH_CAKNA)
          }
          QuickCategoryButton("Konsultasi Ibu Bapa", Icons.Filled.Add) {
            onQuickCreateCategory(ActivityCategory.KONSULTASI_IBUBAPA)
          }
        }
      }
    }

    // Recent Records Preview
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(text = "Catatan Terkini", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onViewAllRecords) { Text("Lihat Semua") }
      }
    }

    if (recentRecords.isEmpty()) {
      item {
        Card(
          colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Tiada catatan lagi. Ketik butang '+' di bawah untuk mula menulis diari kaunselor anda.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
          )
        }
      }
    } else {
      items(recentRecords.take(3), key = { it.id }) { record ->
        CompactRecordPreviewCard(
          record = record,
          accentColor = categoryColors[record.category] ?: MaterialTheme.colorScheme.primary,
          onClick = { onOpenRecord(record) },
        )
      }
    }

    item { Spacer(modifier = Modifier.height(72.dp)) }
  }
}

@Composable
private fun CloudSyncPill(cloudSyncState: CloudSyncState, onClick: () -> Unit) {
  val (icon, label, containerColor, contentColor) =
    when (cloudSyncState) {
      is CloudSyncState.Synced ->
        Quadruple(
          Icons.Filled.CloudDone,
          "Disandarkan (${cloudSyncState.recordCount})",
          MaterialTheme.colorScheme.surfaceContainerHigh,
          MaterialTheme.colorScheme.primary,
        )
      CloudSyncState.Syncing ->
        Quadruple(
          Icons.Filled.CloudSync,
          "Menyegerak awan...",
          MaterialTheme.colorScheme.surfaceContainerHigh,
          MaterialTheme.colorScheme.onSurfaceVariant,
        )
      is CloudSyncState.Error ->
        Quadruple(
          Icons.Filled.CloudSync,
          "Ralat segerakan",
          MaterialTheme.colorScheme.errorContainer,
          MaterialTheme.colorScheme.error,
        )
    }

  Surface(
    color = containerColor,
    shape = RoundedCornerShape(50),
    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = contentColor)
      Spacer(modifier = Modifier.width(5.dp))
      Text(text = label, style = MaterialTheme.typography.labelSmall, color = contentColor)
    }
  }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
private fun KpiBentoCard(
  title: String,
  value: String,
  subtitle: String,
  icon: ImageVector,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = containerColor),
    shape = RoundedCornerShape(18.dp),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelMedium,
          color = contentColor.copy(alpha = 0.85f),
        )
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(18.dp),
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = contentColor,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor.copy(alpha = 0.78f),
      )
    }
  }
}

@Composable
private fun CategoryDonutChart(
  categoryCounts: Map<ActivityCategory, Int>,
  categoryColors: Map<ActivityCategory, Color>,
  totalCount: Int,
  modifier: Modifier = Modifier,
) {
  val progress by
    animateFloatAsState(targetValue = 1f, animationSpec = tween(700), label = "donutAnim")

  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val strokeWidth = 22.dp.toPx()
      val diameter = size.minDimension - strokeWidth
      val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
      val arcSize = Size(diameter, diameter)

      var startAngle = -90f
      val entries = ActivityCategory.entries.filter { (categoryCounts[it] ?: 0) > 0 }
      entries.forEach { cat ->
        val count = categoryCounts[cat] ?: 0
        val sweep = (count.toFloat() / totalCount.toFloat()) * 360f * progress
        drawArc(
          color = categoryColors[cat] ?: Color(0xFF1A6B5A),
          startAngle = startAngle,
          sweepAngle = (sweep - 2.5f).coerceAtLeast(1f),
          useCenter = false,
          topLeft = topLeft,
          size = arcSize,
          style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
        startAngle += sweep
      }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = totalCount.toString(),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = "Rekod",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun WeeklyBarChart(
  points: List<DailyTrendPoint>,
  barColor: Color,
  trackColor: Color,
  labelColor: Color,
) {
  val maxCount = (points.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)

  Column(modifier = Modifier.fillMaxWidth()) {
    Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
      val count = points.size.coerceAtLeast(1)
      val slotWidth = size.width / count
      val barWidth = (slotWidth * 0.48f).coerceAtMost(28.dp.toPx())
      val chartHeight = size.height

      points.forEachIndexed { idx, pt ->
        val x = idx * slotWidth + (slotWidth - barWidth) / 2f
        drawRoundRect(
          color = trackColor,
          topLeft = Offset(x, 0f),
          size = Size(barWidth, chartHeight),
          cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
        )
        if (pt.count > 0) {
          val h = ((pt.count.toFloat() / maxCount.toFloat()) * chartHeight).coerceAtLeast(10.dp.toPx())
          drawRoundRect(
            color = barColor,
            topLeft = Offset(x, chartHeight - h),
            size = Size(barWidth, h),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
          )
        }
      }
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
      points.forEach { pt ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "${pt.count}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (pt.count > 0) barColor else labelColor,
          )
          Text(
            text = pt.dayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
          )
        }
      }
    }
  }
}

@Composable
private fun QuickCategoryButton(label: String, icon: ImageVector, onClick: () -> Unit) {
  AssistChip(
    onClick = onClick,
    label = { Text(label) },
    leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
  )
}

@Composable
private fun CompactRecordPreviewCard(
  record: CounselingRecord,
  accentColor: Color,
  onClick: () -> Unit,
) {
  val dateStr =
    SimpleDateFormat("dd MMM", Locale.forLanguageTag("ms-MY")).format(Date(record.sessionDateMillis))

  Card(
    onClick = onClick,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    shape = RoundedCornerShape(16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier =
          Modifier.width(5.dp).height(44.dp).clip(RoundedCornerShape(4.dp)).background(accentColor)
      )
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = record.clientName.ifBlank { record.category.displayName },
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text =
            "${record.category.shortLabel} • ${record.dayName}, $dateStr • ${record.timeRange}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (record.hasAiSummary) {
        Icon(
          imageVector = Icons.Filled.AutoAwesome,
          contentDescription = "Mempunyai Rumusan AI",
          tint = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@Composable
fun rememberCategoryColorMap(): Map<ActivityCategory, Color> =
  mapOf(
    ActivityCategory.KAUNSELING_INDIVIDU to Color(0xFF1A6B5A),
    ActivityCategory.KAUNSELING_KELOMPOK to Color(0xFF2B8A76),
    ActivityCategory.PROGRAM to Color(0xFFC26D38),
    ActivityCategory.MESYUARAT to Color(0xFF3F5A78),
    ActivityCategory.KELAS_GANTI to Color(0xFF8C6D3F),
    ActivityCategory.ZIARAH_CAKNA to Color(0xFF7B4E73),
    ActivityCategory.KONSULTASI_IBUBAPA to Color(0xFF567A46),
  )
