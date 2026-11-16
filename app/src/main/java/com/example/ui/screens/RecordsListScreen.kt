package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.ActivityCategory
import com.example.model.CounselingRecord
import com.example.ui.components.CategoryPillBadge
import com.example.ui.components.CategoryVisual
import com.example.ui.components.ClientAvatarBadge
import com.example.ui.components.rememberCategoryVisualMap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordsListScreen(
  records: List<CounselingRecord>,
  selectedCategory: ActivityCategory?,
  searchQuery: String,
  onCategorySelected: (ActivityCategory?) -> Unit,
  onSearchQueryChanged: (String) -> Unit,
  onOpenRecord: (CounselingRecord) -> Unit,
  onCreateNew: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val categoryVisuals = rememberCategoryVisualMap()
  val filtered =
    records.filter { record ->
      val catMatch = selectedCategory == null || record.category == selectedCategory
      val q = searchQuery.trim().lowercase()
      val queryMatch =
        q.isEmpty() ||
          record.clientName.lowercase().contains(q) ||
          record.className.lowercase().contains(q) ||
          record.organizer.lowercase().contains(q) ||
          record.guardianName.lowercase().contains(q) ||
          record.guardianPhone.lowercase().contains(q) ||
          record.notes.lowercase().contains(q) ||
          record.summaryMatter.lowercase().contains(q) ||
          record.programStrengthsWeaknesses.lowercase().contains(q) ||
          record.category.displayName.lowercase().contains(q)
      catMatch && queryMatch
    }

  Column(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Diari & Rekod Kaunselor",
            style = MaterialTheme.typography.headlineMedium,
          )
          Text(
            text = "Semua catatan disulitkan secara automatik",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(50),
        ) {
          Text(
            text = "${filtered.size} Rekod",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChanged,
        placeholder = { Text("Cari nama klien, kelas, atau catatan...") },
        leadingIcon = {
          Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchQueryChanged("") }) {
              Icon(Icons.Filled.Close, contentDescription = "Padam carian")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors =
          OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
          ),
        modifier = Modifier.fillMaxWidth().testTag("search_records_input"),
      )
    }

    LazyRow(
      contentPadding = PaddingValues(horizontal = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item {
        FilterChip(
          selected = selectedCategory == null,
          onClick = { onCategorySelected(null) },
          label = { Text("Semua (${records.size})") },
          shape = RoundedCornerShape(50),
          modifier = Modifier.testTag("filter_cat_all"),
        )
      }
      items(ActivityCategory.entries) { cat ->
        val count = records.count { it.category == cat }
        val visual = categoryVisuals[cat]
        val accent = visual?.color ?: MaterialTheme.colorScheme.primary
        FilterChip(
          selected = selectedCategory == cat,
          onClick = { onCategorySelected(if (selectedCategory == cat) null else cat) },
          leadingIcon =
            visual?.let {
              {
                Icon(
                  imageVector = it.icon,
                  contentDescription = null,
                  modifier = Modifier.size(15.dp),
                  tint = if (selectedCategory == cat) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            },
          label = { Text("${cat.shortLabel} ($count)") },
          shape = RoundedCornerShape(50),
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = accent.copy(alpha = 0.14f),
              selectedLabelColor = accent,
            ),
          modifier = Modifier.testTag("filter_cat_${cat.name.lowercase()}"),
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    if (filtered.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          shape = RoundedCornerShape(26.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_empty_diary),
              contentDescription = "Tiada rekod ditemui",
              modifier = Modifier.size(164.dp).clip(RoundedCornerShape(24.dp)),
              contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text =
                if (records.isEmpty()) "Diari Kaunselor Masih Kosong"
                else "Tiada Rekod Sepadan Carian",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text =
                if (records.isEmpty()) {
                  "Rekodkan sesi kaunseling individu, kelompok, program, atau ziarah cakna pertama anda."
                } else {
                  "Cuba tukar kata kunci carian atau penapis kategori di atas."
                },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
            )
            if (records.isEmpty()) {
              Spacer(modifier = Modifier.height(18.dp))
              Button(
                onClick = onCreateNew,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(48.dp).testTag("empty_create_record_button"),
              ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tambah Catatan Baharu")
              }
            }
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        items(filtered, key = { it.id }) { record ->
          val visual = categoryVisuals[record.category]
          val accent = visual?.color ?: MaterialTheme.colorScheme.primary
          val dateText =
            SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("ms-MY"))
              .format(Date(record.sessionDateMillis))

          Card(
            onClick = { onOpenRecord(record) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().testTag("record_item_card_${record.id}"),
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                CategoryPillBadge(
                  category = record.category,
                  visual = visual ?: CategoryVisual(accent, Icons.Filled.Lock),
                )
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Box(
                    modifier =
                      Modifier.clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f))
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Lock,
                      contentDescription = "Disulitkan E2EE",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(13.dp),
                    )
                  }
                  if (record.hasAiSummary) {
                    Surface(
                      color = MaterialTheme.colorScheme.secondaryContainer,
                      shape = RoundedCornerShape(50),
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Icon(
                          imageVector = Icons.Filled.AutoAwesome,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.secondary,
                          modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                          text = "AI",
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = FontWeight.Bold,
                          color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                      }
                    }
                  }
                }
              }

              Spacer(modifier = Modifier.height(12.dp))

              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
              ) {
                val cardTitle =
                  listOf(record.clientName, record.className, record.organizer, record.guardianName)
                    .filter { it.isNotBlank() }
                    .take(2)
                    .joinToString(" • ")
                    .ifBlank { record.category.displayName }
                ClientAvatarBadge(
                  name = cardTitle,
                  accentColor = accent,
                  size = 44.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = cardTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Spacer(modifier = Modifier.height(2.dp))
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector = Icons.Filled.Schedule,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = "${record.dayName}, $dateText • ${record.timeRange} (${record.durationMinutes} min)",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
              }

              val preview = record.summaryMatter.ifBlank { record.notes }
              if (preview.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                  color = MaterialTheme.colorScheme.surfaceContainer,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
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
