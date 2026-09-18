package com.reader343.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import java.io.File

@Composable
fun BookCover(
    title: String,
    coverPath: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.appShapes.tile,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.appColors.surf2, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (coverPath != null) {
            AsyncImage(
                model = File(coverPath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title.take(1).uppercase(currentLocale()),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.appColors.ink3,
            )
        }
    }
}
