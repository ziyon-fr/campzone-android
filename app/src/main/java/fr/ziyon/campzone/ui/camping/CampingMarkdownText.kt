package fr.ziyon.campzone.ui.camping

import android.text.TextUtils
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin

@Composable
internal fun CampingMarkdownText(
    text: String,
    textColor: Int,
    modifier: Modifier = Modifier,
    textSizeSp: Float = 15f,
    maxLines: Int? = null,
) {
    val context = LocalContext.current
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .build()
    }
    val spanned = remember(text) { markwon.toMarkdown(text) }

    AndroidView(
        factory = { ctx ->
            TextView(ctx).apply {
                textSize = textSizeSp
                setLineSpacing(4f, 1f)
            }
        },
        update = { tv ->
            tv.setTextColor(textColor)
            if (maxLines != null) {
                tv.maxLines = maxLines
                tv.ellipsize = TextUtils.TruncateAt.END
            } else {
                tv.maxLines = Int.MAX_VALUE
                tv.ellipsize = null
            }
            markwon.setParsedMarkdown(tv, spanned)
        },
        modifier = modifier,
    )
}
