package com.guhao.opensource.cutme.android

import android.Manifest.permission.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.BottomCenter
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.guhao.opensource.cutme.millisTimeFormat
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.io.Serializable

class SelectIngredientsActivity: ComponentActivity() {
    private fun requestPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, READ_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE),1)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(WRITE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE),1)
            }
            false
        } else true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if(!requestPermission()) return

        val list = ArrayList<SelectInfo>()
        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION),
            null, null,
            MediaStore.Video.VideoColumns.DATE_MODIFIED).use {
                while(it!!.moveToNext()) {
                    list.add(SelectInfo(
                        path = it.getString(it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)),
                        duration = it.getLong(it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
                    ))
                }
        }

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media.DATA),
            null, null,
            MediaStore.Images.ImageColumns.DATE_MODIFIED).use {
                while(it!!.moveToNext()) {
                    list.add(SelectInfo(path = it.getString(it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))))
                }
        }
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_MODIFIED),
            null, null,
            MediaStore.Audio.AudioColumns.DATE_MODIFIED).use {
            while(it!!.moveToNext()) {
                list.add(SelectInfo(
                        path = it.getString(it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)),
                        duration = it.getLong(it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))))
            }
        }


        setContent {
            MyApplicationTheme {
                Select(
                    list = list,
                    onSelected = { selectInfos ->

                        setResult(RESULT_OK, Intent().apply {
                            putExtra("selected", ByteArrayOutputStream().apply {
                                ObjectOutputStream(this).use {
                                    it.writeObject(selectInfos)
                                }
                            }.toByteArray())
                        })
                        finish()
                    })
            }
        }
    }
}

class SelectInfo(
    val path: String,
    val dateModified: Long = 0, // todo
    val duration: Long? = null // In milli seconds
): Serializable

@OptIn(ExperimentalGlideComposeApi::class, ExperimentalFoundationApi::class)
@Composable
fun Select(
    list: ArrayList<SelectInfo>,
    onSelected: (List<SelectInfo>) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        var selectedList by remember { mutableStateOf(listOf<SelectInfo>()) }
        val selectionMode = selectedList.isNotEmpty()

        LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Fixed(count = 3)) {
            items(list) { currentInfo ->
                Box {
                    Card(
                        modifier = Modifier.padding(5.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                        shape = RoundedCornerShape(30.dp),
                    ) {
                        val selected = selectedList.contains(currentInfo)

                        fun multiSelectedAction() {
                            selectedList = if (selected)
                                selectedList.filter { it != currentInfo }
                            else
                                selectedList + listOf(currentInfo)
                        }
                        Box(modifier = Modifier
                            .fillMaxSize()
                            .combinedClickable(
                                onClick = {
                                    if (selectionMode) {
                                        multiSelectedAction()
                                    } else onSelected.invoke(listOf(currentInfo))
                                },
                                onLongClick = {
                                    multiSelectedAction()
                                }
                            )) {
                            AnimatedContent(targetState = selected, label = "selected") {
                                GlideImage(
                                    modifier = Modifier.alpha(if(it) 0.5f else 1f),
                                    model = currentInfo.path,
                                    contentDescription = "")
                            }

                            Column(modifier = Modifier.align(Alignment.Center)) {
                                currentInfo.duration?.let {
                                    Text(
                                        text = it.millisTimeFormat(),
                                        color = Color.White)
                                }

                                AnimatedVisibility(
                                    modifier = Modifier.align(CenterHorizontally),
                                    visible = selected) {
                                    Text(
                                        modifier = Modifier.padding(1.dp),
                                        text = (selectedList.indexOf(currentInfo) + 1).toString(),
                                        textDecoration = TextDecoration.Underline,
                                        color = Color.White)
                                }
                            }

                        }

                    }


                }
            }
        }

        AnimatedVisibility(
            modifier = Modifier
                .align(BottomCenter)
                .padding(bottom = 50.dp),
            enter = fadeIn(),
            exit = fadeOut(),
            visible = selectionMode) {
            FloatingActionButton(
                shape = RoundedCornerShape(50.dp),
                onClick = {
                    onSelected(selectedList)
                }
            ) {
                 Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
            }
        }

    }
}