package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.data.CountriesData
import com.example.core.data.CountryItem
import com.example.core.localization.LocalAppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySelectorSheet(
    selectedCountry: CountryItem,
    recentCountryIsos: List<String>,
    onSelect: (CountryItem) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            CountriesData.allCountries
        } else {
            CountriesData.allCountries.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.callingCode.contains(q, ignoreCase = true) ||
                    it.isoCode.contains(q, ignoreCase = true)
            }
        }
    }

    val recentItems = remember(recentCountryIsos) {
        recentCountryIsos.mapNotNull { iso ->
            CountriesData.allCountries.firstOrNull { it.isoCode.equals(iso, ignoreCase = true) }
        }
    }

    val popularItems = remember {
        CountriesData.popularIsoCodes.mapNotNull { iso ->
            CountriesData.allCountries.firstOrNull { it.isoCode == iso }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = strings.selectCountryTitle,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(strings.searchCountryHint) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = strings.searchCountryHint) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("country_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (searchQuery.isBlank() && recentItems.isNotEmpty()) {
                    item {
                        SectionHeader(strings.recentCountries)
                    }
                    items(recentItems, key = { "recent_${it.isoCode}" }) { item ->
                        CountryRow(item = item, isSelected = item.isoCode == selectedCountry.isoCode) {
                            onSelect(item)
                        }
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                }

                if (searchQuery.isBlank()) {
                    item {
                        SectionHeader(strings.popularCountries)
                    }
                    items(popularItems, key = { "pop_${it.isoCode}" }) { item ->
                        CountryRow(item = item, isSelected = item.isoCode == selectedCountry.isoCode) {
                            onSelect(item)
                        }
                    }
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
                    item {
                        SectionHeader(strings.allCountries)
                    }
                }

                items(filtered, key = { "all_${it.isoCode}" }) { item ->
                    CountryRow(item = item, isSelected = item.isoCode == selectedCountry.isoCode) {
                        onSelect(item)
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
    )
}

@Composable
private fun CountryRow(
    item: CountryItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("country_row_${item.isoCode}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = item.flagEmoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = item.callingCode,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
