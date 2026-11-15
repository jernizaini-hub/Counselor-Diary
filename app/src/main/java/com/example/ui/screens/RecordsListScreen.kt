package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
  val categoryColors = rememberCategoryColorMap()
  val filtered =
    records.filter { record ->
      val catMatch = selectedCategory == null || record.category == selectedCategory
      val q = searchQuery.trim().lowercase()
      val queryMatch =
        q.isEmpty() ||
          record.clientName.lowercase().contains(q) ||
          record.className.lowercase().contains(q) ||
          record.notes.lowercase().contains(q) ||
          record.summaryMatter.lowercase().contains(q) ||
          record.category.displayName.lowercase().contains(q)
      catMatch && queryMatch
    }

  Column(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
      Text(
        text = "Diari & Rekod Kaunselor",
        style = MaterialTheme.typography.headlineMedium,
      )
      Spacer(modifier = Modifier.height(10.dp))
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChanged,
        placeholder = { Text("Cari nama klien, kelas, atau catatan...") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchQueryChanged("") }) {
              Icon(Icons.Filled.Close, contentDescription = "Padam carian")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
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
          modifier = Modifier.testTag("filter_cat_all"),
        )
      }
      items(ActivityCategory.entries) { cat ->
        val count = records.count { it.category == cat }
        FilterChip(
          selected = selectedCategory == cat,
          onClick = { onCategorySelected(if (selectedCategory == cat) null else cat) },
          label = { Text("${cat.shortLabel} ($count)") },
          modifier = Modifier.testTag("filter_cat_${cat.name.lowercase()}"),
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (filtered.isEmpty()) {
      Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Image(
            painter = painterResource(id = R.drawable.img_empty_diary),
            contentDescription = "Tiada rekod ditemui",
            modifier = Modifier.size(168.dp).clip(RoundedCornerShape(24.dp)),
            contentScale = ContentScale.Crop,
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text =
              if (records.isEmpty()) "Diari Kaunselor Masih Kosong"
              else "Tiada Rekod Sepadan Carian",
            style = MaterialTheme.typography.titleMedium,
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
            Spacer(modifier = Modifier.height(16.dp))
            Button(
              onClick = onCreateNew,
              modifier = Modifier.testTag("empty_create_record_button"),
            ) {
              Icon(Icons.Filled.Add, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Tambah Catatan Baharu")
            }
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        items(filtered, key = { it.id }) { record ->
          val accent = categoryColors[record.category] ?: MaterialTheme.colorScheme.primary
          val dateText =
            SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("ms-MY"))
              .format(Date(record.sessionDateMillis))

          Card(
            onClick = { onOpenRecord(record) },
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
              ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().testTag("record_item_card_${record.id}"),
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Surface(
                  color = accent.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(50),
                ) {
                  Text(
                    text = record.category.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  )
                }
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Disulitkan E2EE",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                  )
                  if (record.hasAiSummary) {
                    Surface(
                      color = MaterialTheme.colorScheme.secondaryContainer,
                      shape = RoundedCornerShape(50),
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Icon(
                          imageVector = Icons.Filled.AutoAwesome,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.onSecondaryContainer,
                          modifier = Modifier.size(12.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                          text = "AI",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                      }
                    }
                  }
                }
              }

              Spacer(modifier = Modifier.height(10.dp))

              Text(
                text =
                  listOf(record.clientName, record.className)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                    .ifBlank { record.category.displayName },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )

              Spacer(modifier = Modifier.height(4.dp))

              Text(
                text = "${record.dayName}, $dateText • ${record.timeRange} (${record.durationMinutes} min)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )

              val preview = record.summaryMatter.ifBlank { record.notes }
              if (preview.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = preview,
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
    }
  }
}
