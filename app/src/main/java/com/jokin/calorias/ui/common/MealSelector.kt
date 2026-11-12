package com.jokin.calorias.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jokin.calorias.domain.MealType
import com.jokin.calorias.i18n.labelRes

@Composable
fun MealSelector(selected: MealType, onSelect: (MealType) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MealType.entries.forEach { m ->
            FilterChip(selected = m == selected, onClick = { onSelect(m) }, label = { Text(stringResource(m.labelRes())) })
        }
    }
}
