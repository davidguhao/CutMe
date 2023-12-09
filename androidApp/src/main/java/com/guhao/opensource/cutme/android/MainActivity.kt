package com.guhao.opensource.cutme.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import java.io.ByteArrayInputStream
import java.io.ObjectInputStream

class MainActivity : ComponentActivity() {
    private val vm by lazy {
        ViewModelProvider(this)[MainViewModel::class.java].apply {
            requestAdding = {
                launcher.launch(it)
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                Main(vm)
            }
        }
    }

    private val launcher = registerForActivityResult(
        object : ActivityResultContract<(List<SelectInfo>) -> Unit, Any?>() {
            override fun parseResult(resultCode: Int, intent: Intent?): Any? {

                intent?.getByteArrayExtra("selected")?.let {
                    onFileLoadedListener?.invoke(
                        ObjectInputStream(ByteArrayInputStream(it)).readObject() as List<SelectInfo>
                    )
                }

                return null
            }

            var onFileLoadedListener: ((List<SelectInfo>) -> Unit)? = null
            override fun createIntent(
                context: Context,
                input: (List<SelectInfo>) -> Unit
            ): Intent {
                onFileLoadedListener = input

                return Intent(context, SelectIngredientsActivity::class.java)
            }
        }
    ) {

    }
}

@Composable
fun Greeting(requestGreetingEnd: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            modifier = Modifier
                .align(Alignment.Center)
                .clickable { requestGreetingEnd() },
            text = stringResource(id = R.string.hello),
            fontSize = MaterialTheme.typography.titleLarge.fontSize)
    }
}