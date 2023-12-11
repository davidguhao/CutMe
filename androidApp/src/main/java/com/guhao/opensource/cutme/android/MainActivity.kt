package com.guhao.opensource.cutme.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
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