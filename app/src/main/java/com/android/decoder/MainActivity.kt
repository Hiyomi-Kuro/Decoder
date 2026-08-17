package com.android.decoder

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.decoder.qmc.QmcDecoder
import com.android.decoder.ui.theme.DecoderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DecoderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DecoderScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun DecoderScreen(
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("请选择需要转换的 QMC 文件") }
    var isDecoding by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { outputUri ->
        val inputUri = selectedUri

        if (outputUri == null || inputUri == null) {
            if (!isDecoding) {
                status = "已取消保存"
            }
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            isDecoding = true
            status = "正在转换，请稍候……"

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(inputUri).use { input ->
                        requireNotNull(input) {
                            "无法打开输入文件"
                        }

                        context.contentResolver.openOutputStream(
                            outputUri,
                            "w"
                        ).use { output ->
                            requireNotNull(output) {
                                "无法创建输出文件"
                            }

                            QmcDecoder.decode(
                                input = input,
                                output = output
                            )
                        }
                    }
                }
            }

            isDecoding = false

            status = result.fold(
                onSuccess = { bytes ->
                    "转换完成：已处理 ${formatBytes(bytes)}"
                },
                onFailure = { error ->
                    "转换失败：${error.message ?: error.javaClass.simpleName}"
                }
            )
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            status = "未选择文件"
            return@rememberLauncherForActivityResult
        }

        val name = queryDisplayName(context.contentResolver, uri)
            ?: "selected_file"

        val extension = QmcDecoder.outputExtension(name)

        if (extension == null) {
            selectedUri = null
            selectedName = null
            status = "不支持该文件：$name"
        } else {
            selectedUri = uri
            selectedName = name
            status = "已选择：$name"
        }
    }

    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "QMC Decoder",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "支持 .qmc0 / .qmc3 / .qmcflac / .qmcogg",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !isDecoding,
            onClick = {
                openLauncher.launch(arrayOf("*/*"))
            }
        ) {
            Text("选择 QMC 文件")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedUri != null && !isDecoding,
            onClick = {
                val name = selectedName ?: return@Button
                val outputExtension =
                    QmcDecoder.outputExtension(name) ?: return@Button

                val baseName = name.substringBeforeLast('.', name)
                val outputName = "$baseName.$outputExtension"

                saveLauncher.launch(outputName)
            }
        ) {
            Text("转换并保存")
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isDecoding) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = status,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private fun queryDisplayName(
    resolver: android.content.ContentResolver,
    uri: Uri
): String? {
    resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

        if (index >= 0 && cursor.moveToFirst()) {
            return cursor.getString(index)
        }
    }

    return null
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024L * 1024L * 1024L ->
            "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))

        bytes >= 1024L * 1024L ->
            "%.2f MB".format(bytes / (1024.0 * 1024.0))

        bytes >= 1024L ->
            "%.2f KB".format(bytes / 1024.0)

        else ->
            "$bytes B"
    }
}
