package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.components.CategoryPillBadge
import com.example.ui.components.ClientAvatarBadge
import com.example.ui.components.rememberCategoryVisualMap
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentMint
import com.example.ui.theme.SagePrimaryBright
import com.example.ui.viewmodel.CloudSyncState
import com.example.ui.viewmodel.DailyTrendPoint
import com.example.ui.viewmodel.DashboardStats
import com.example.ui.viewmodel.StatsPeriod
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
  val categoryVisuals = rememberCategoryVisualMap()
  val todayText =
    SimpleDateFormat("EEEE, dd MMM yyyy", Locale.forLanguageTag("ms-MY")).format(Date())

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    // 1. Cheerful Gradient Hero Greeting Card
    item {
      val heroGradient =
        Brush.linearGradient(
          colors =
            listOf(
              MaterialTheme.colorScheme.primary,
              SagePrimaryBright,
              AccentEmerald,
            )
        )
      Surface(
        shape = RoundedCornerShape(26.dp),
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .background(heroGradient)
              .drawBehind {
                drawCircle(
                  color = Color.White.copy(alpha = 0.11f),
                  radius = 120.dp.toPx(),
                  center = Offset(size.width * 0.92f, size.height * 0.15f),
                )
                drawCircle(
                  color = Color.White.copy(alpha = 0.08f),
                  radius = 70.dp.toPx(),
                  center = Offset(size.width * 0.72f, size.height * 0.95f),
                )
              }
              .padding(20.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                imageVector = Icons.Filled.WbSunny,
                contentDescription = null,
                tint = Color(0xFFFEF08A),
                modifier = Modifier.size(16.dp),
              )
              Text(
                text = todayText.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.92f),
              )
            }

            Text(
              text = "Selamat Sejahtera,\n${counselorName.ifBlank { "Kaunselor" }}",
              style = MaterialTheme.typography.headlineMedium,
              color = Color.White,
              fontWeight = FontWeight.Bold,
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              CloudSyncPill(cloudSyncState = cloudSyncState, onClick = onOpenSecurity)
              Surface(
                color = Color.White.copy(alpha = 0.20f),
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
                    tint = Color.White,
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "E2EE Aktif",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                  )
                }
              }
            }
          }
        }
      }
    }

    // 2. Modern Pill Segmented Period Selector
    item {
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          StatsPeriod.entries.forEach { period ->
            val selected = activePeriod == period
            Surface(
              onClick = { onPeriodSelected(period) },
              color =
                if (selected) MaterialTheme.colorScheme.surface
                else Color.Transparent,
              shadowElevation = if (selected) 2.dp else 0.dp,
              shape = RoundedCornerShape(12.dp),
              modifier =
                Modifier.weight(1f).testTag("stats_period_${period.name.lowercase()}"),
            ) {
              Box(
                modifier = Modifier.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = period.label,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                  color =
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                )
              }
            }
          }
        }
      }
    }

    // 3. Cheerful 2x2 KPI Bento Grid
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
            accentColor = AccentMint,
            modifier = Modifier.weight(1f),
          )
          KpiBentoCard(
            title = "Sesi Kaunseling",
            value = stats.counselingSessionsCount.toString(),
            subtitle = "Individu & Kelompok",
            icon = Icons.Filled.Person,
            accentColor = AccentCoral,
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
            accentColor = AccentIndigo,
            modifier = Modifier.weight(1f),
          )
          val aiPct =
            if (stats.totalRecords > 0) (stats.aiSummaryCount * 100) / stats.totalRecords else 0
          KpiBentoCard(
            title = "Rumusan AI",
            value = "$aiPct%",
            subtitle = "${stats.totalMinutes} min jumlah",
            icon = Icons.Filled.AutoAwesome,
            accentColor = AccentEmerald,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }

    // 4. Cheerful Quick Action Carousel
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Mula Catatan Pantas",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          items(ActivityCategory.entries) { cat ->
            val visual = categoryVisuals[cat]
            val accent = visual?.color ?: MaterialTheme.colorScheme.primary
            val icon = visual?.icon ?: Icons.Filled.Person
            Surface(
              onClick = { onQuickCreateCategory(cat) },
              color = MaterialTheme.colorScheme.surface,
              shape = RoundedCornerShape(18.dp),
              border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
              shadowElevation = 1.dp,
            ) {
              Column(
                modifier = Modifier.width(114.dp).padding(12.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Box(
                  modifier =
                    Modifier.size(36.dp)
                      .clip(RoundedCornerShape(11.dp))
                      .background(accent.copy(alpha = 0.14f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp),
                  )
                }
                Text(
                  text = cat.shortLabel,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
    }

    // 5. Visual Donut Chart: Pecahan Kategori Prestasi Semasa
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().testTag("dashboard_category_chart_card"),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Analisis Pecahan Kategori",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "Taburan sesi & aktiviti (${activePeriod.label})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Box(
              modifier =
                Modifier.size(36.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Filled.Insights,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
              )
            }
          }
          Spacer(modifier = Modifier.height(16.dp))

          if (stats.totalRecords == 0) {
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainer,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = "Belum ada rekod untuk tempoh ini. Ketik mana-mana kad Catatan Pantas di atas untuk mula!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(14.dp),
              )
            }
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
              CategoryDonutChart(
                categoryCounts = stats.categoryCounts,
                categoryColors = categoryVisuals.mapValues { it.value.color },
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                totalCount = stats.totalRecords,
                modifier = Modifier.size(136.dp),
              )
              Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
              ) {
                ActivityCategory.entries.forEach { cat ->
                  val count = stats.categoryCounts[cat] ?: 0
                  if (count > 0) {
                    val pct = (count * 100) / stats.totalRecords
                    val color = categoryVisuals[cat]?.color ?: MaterialTheme.colorScheme.primary
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
                          modifier = Modifier.size(10.dp).clip(CircleShape).background(color)
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
                        text = "$count ($pct%)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = color,
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

    // 6. Visual 7-Day Activity Bar Chart
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().testTag("dashboard_trend_chart_card"),
      ) {
        Column(modifier = Modifier.padding(18.dp)) {
          Text(
            text = "Trend Prestasi 7 Hari Terakhir",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = "Kekerapan harian sesi dan aktiviti kaunselor",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(16.dp))
          WeeklyBarChart(
            points = stats.weeklyTrend,
            barColorTop = SagePrimaryBright,
            barColorBottom = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    // 7. Recent Records Preview
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Catatan Terkini",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        TextButton(onClick = onViewAllRecords) {
          Text("Lihat Semua")
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }

    if (recentRecords.isEmpty()) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          shape = RoundedCornerShape(18.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Tiada catatan lagi. Ketik butang 'Catatan Baharu' di bawah untuk mula menulis diari kaunselor anda.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(18.dp),
          )
        }
      }
    } else {
      items(recentRecords.take(3), key = { it.id }) { record ->
        val visual = categoryVisuals[record.category]
        CompactRecordPreviewCard(
          record = record,
          accentColor = visual?.color ?: MaterialTheme.colorScheme.primary,
          visualIcon = visual?.icon ?: Icons.Filled.Person,
          onClick = { onOpenRecord(record) },
        )
      }
    }

    item { Spacer(modifier = Modifier.height(76.dp)) }
  }
}

@Composable
private fun CloudSyncPill(cloudSyncState: CloudSyncState, onClick: () -> Unit) {
  val (icon, label, bgColor) =
    when (cloudSyncState) {
      is CloudSyncState.Synced ->
        Triple(
          Icons.Filled.CloudDone,
          "Disandarkan (${cloudSyncState.recordCount})",
          Color.White.copy(alpha = 0.22f),
        )
      CloudSyncState.Syncing ->
        Triple(
          Icons.Filled.CloudSync,
          "Menyegerak awan...",
          Color.White.copy(alpha = 0.18f),
        )
      is CloudSyncState.Error ->
        Triple(
          Icons.Filled.CloudSync,
          "Ralat segerakan",
          Color(0xFFEF4444).copy(alpha = 0.85f),
        )
    }

  Surface(
    color = bgColor,
    shape = RoundedCornerShape(50),
    modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
      Spacer(modifier = Modifier.width(5.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Composable
private fun KpiBentoCard(
  title: String,
  value: String,
  subtitle: String,
  icon: ImageVector,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(20.dp),
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.24f)),
    shadowElevation = 1.dp,
    modifier = modifier,
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth().drawBehind {
          drawCircle(
            color = accentColor.copy(alpha = 0.08f),
            radius = 48.dp.toPx(),
            center = Offset(size.width * 0.92f, size.height * 0.15f),
          )
        }
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Box(
            modifier =
              Modifier.size(30.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = icon,
              contentDescription = null,
              tint = accentColor,
              modifier = Modifier.size(16.dp),
            )
          }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = value,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = accentColor,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

@Composable
private fun CategoryDonutChart(
  categoryCounts: Map<ActivityCategory, Int>,
  categoryColors: Map<ActivityCategory, Color>,
  trackColor: Color,
  totalCount: Int,
  modifier: Modifier = Modifier,
) {
  val progress by
    animateFloatAsState(targetValue = 1f, animationSpec = tween(750), label = "donutAnim")

  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val strokeWidth = 20.dp.toPx()
      val diameter = size.minDimension - strokeWidth
      val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
      val arcSize = Size(diameter, diameter)

      drawArc(
        color = trackColor,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokeWidth),
      )

      var startAngle = -90f
      val entries = ActivityCategory.entries.filter { (categoryCounts[it] ?: 0) > 0 }
      entries.forEach { cat ->
        val count = categoryCounts[cat] ?: 0
        val sweep = (count.toFloat() / totalCount.toFloat()) * 360f * progress
        drawArc(
          color = categoryColors[cat] ?: AccentMint,
          startAngle = startAngle,
          sweepAngle = (sweep - 3f).coerceAtLeast(1f),
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
  barColorTop: Color,
  barColorBottom: Color,
  trackColor: Color,
  labelColor: Color,
) {
  val maxCount = (points.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)

  Column(modifier = Modifier.fillMaxWidth()) {
    Canvas(modifier = Modifier.fillMaxWidth().height(116.dp)) {
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
          cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
        )
        if (pt.count > 0) {
          val h = ((pt.count.toFloat() / maxCount.toFloat()) * chartHeight).coerceAtLeast(12.dp.toPx())
          drawRoundRect(
            brush = Brush.verticalGradient(listOf(barColorTop, barColorBottom)),
            topLeft = Offset(x, chartHeight - h),
            size = Size(barWidth, h),
            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
          )
        }
      }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
      points.forEach { pt ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "${pt.count}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (pt.count > 0) barColorBottom else labelColor,
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
private fun CompactRecordPreviewCard(
  record: CounselingRecord,
  accentColor: Color,
  visualIcon: ImageVector,
  onClick: () -> Unit,
) {
  val dateStr =
    SimpleDateFormat("dd MMM", Locale.forLanguageTag("ms-MY")).format(Date(record.sessionDateMillis))

  Card(
    onClick = onClick,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    shape = RoundedCornerShape(18.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      ClientAvatarBadge(
        name = record.clientName.ifBlank { record.category.shortLabel },
        accentColor = accentColor,
        size = 44.dp,
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = record.clientName.ifBlank { record.category.displayName },
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${record.dayName}, $dateStr • ${record.timeRange}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        CategoryPillBadge(
          category = record.category,
          visual = com.example.ui.components.CategoryVisual(accentColor, visualIcon),
        )
      }
      if (record.hasAiSummary) {
        Box(
          modifier =
            Modifier.size(32.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.secondaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.AutoAwesome,
            contentDescription = "Mempunyai Rumusan AI",
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }
  }
}
