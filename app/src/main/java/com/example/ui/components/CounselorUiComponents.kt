package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HomeWork
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.ActivityCategory
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.AccentBerry
import com.example.ui.theme.AccentCoral
import com.example.ui.theme.AccentEmerald
import com.example.ui.theme.AccentIndigo
import com.example.ui.theme.AccentMint
import com.example.ui.theme.AccentSky

data class CategoryVisual(
  val color: Color,
  val icon: ImageVector,
)

@Composable
fun rememberCategoryVisualMap(): Map<ActivityCategory, CategoryVisual> =
  mapOf(
    ActivityCategory.KAUNSELING_INDIVIDU to CategoryVisual(AccentMint, Icons.Filled.Person),
    ActivityCategory.KAUNSELING_KELOMPOK to CategoryVisual(AccentEmerald, Icons.Filled.Groups),
    ActivityCategory.PROGRAM to CategoryVisual(AccentCoral, Icons.Filled.Campaign),
    ActivityCategory.MESYUARAT to CategoryVisual(AccentIndigo, Icons.Filled.EventNote),
    ActivityCategory.KELAS_GANTI to CategoryVisual(AccentAmber, Icons.Filled.School),
    ActivityCategory.ZIARAH_CAKNA to CategoryVisual(AccentBerry, Icons.Filled.HomeWork),
    ActivityCategory.KONSULTASI_IBUBAPA to CategoryVisual(AccentSky, Icons.Filled.FamilyRestroom),
  )

@Composable
fun rememberCategoryColorMap(): Map<ActivityCategory, Color> =
  rememberCategoryVisualMap().mapValues { it.value.color }

@Composable
fun CategoryPillBadge(
  category: ActivityCategory,
  visual: CategoryVisual,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = visual.color.copy(alpha = 0.13f),
    shape = RoundedCornerShape(50),
    border = BorderStroke(0.8.dp, visual.color.copy(alpha = 0.28f)),
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      Icon(
        imageVector = visual.icon,
        contentDescription = null,
        tint = visual.color,
        modifier = Modifier.size(13.dp),
      )
      Text(
        text = category.displayName,
        style = MaterialTheme.typography.labelSmall,
        color = visual.color,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
fun ClientAvatarBadge(
  name: String,
  accentColor: Color,
  size: Dp = 44.dp,
  modifier: Modifier = Modifier,
) {
  val initial =
    name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "K"

  Box(
    modifier =
      modifier
        .size(size)
        .clip(CircleShape)
        .background(accentColor.copy(alpha = 0.14f)),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = initial,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = accentColor,
    )
  }
}

@Composable
fun SectionStepHeader(
  stepNumber: String,
  title: String,
  subtitle: String? = null,
  accentColor: Color = MaterialTheme.colorScheme.primary,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier.size(28.dp)
          .clip(CircleShape)
          .background(accentColor.copy(alpha = 0.15f)),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = stepNumber,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = accentColor,
      )
    }
    Spacer(modifier = Modifier.width(10.dp))
    androidx.compose.foundation.layout.Column {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
