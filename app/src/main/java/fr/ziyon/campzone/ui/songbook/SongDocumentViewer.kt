package fr.ziyon.campzone.ui.songbook

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import fr.ziyon.campzone.R
import fr.ziyon.campzone.core.designsystem.CzButton
import fr.ziyon.campzone.core.designsystem.CzButtonVariant
import fr.ziyon.campzone.core.designsystem.CzEmptyState
import fr.ziyon.campzone.core.designsystem.CzErrorState
import fr.ziyon.campzone.core.designsystem.CzLoadingView
import fr.ziyon.campzone.core.designsystem.CzRadius
import fr.ziyon.campzone.core.designsystem.CzSpacing
import fr.ziyon.campzone.core.designsystem.CzTypeScale
import fr.ziyon.campzone.core.designsystem.czColors
import fr.ziyon.campzone.data.model.Song
import fr.ziyon.campzone.data.songbook.SongDocumentDownloader
import fr.ziyon.campzone.data.songbook.SongDocumentItem
import fr.ziyon.campzone.data.songbook.SongDocumentKind
import fr.ziyon.campzone.data.songbook.SongPresentationDeckBuilder
import fr.ziyon.campzone.data.songbook.SongPresentationSlide
import fr.ziyon.campzone.data.songbook.SongPresentationSlideKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import kotlin.math.roundToInt

@Composable
fun SongDocumentViewerDialog(
    document: SongDocumentItem,
    onDismiss: () -> Unit,
) {
    when (document.kind) {
        SongDocumentKind.SheetPdf -> SongSheetReaderDialog(document = document, onDismiss = onDismiss)
        SongDocumentKind.Slides -> SongPptxPresenterDialog(document = document, onDismiss = onDismiss)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongSheetReaderDialog(
    document: SongDocumentItem,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val context = LocalContext.current
    val downloader = remember(context) { SongDocumentDownloader(context) }
    var retryNonce by remember(document.id) { mutableStateOf(0) }
    val loadState by produceState<DocumentLoadState>(DocumentLoadState.Loading, document.id, retryNonce) {
        value = DocumentLoadState.Loading
        value = runCatching { downloader.localFile(document.remoteUrl, document.kind) }
            .fold(
                onSuccess = DocumentLoadState::Loaded,
                onFailure = { DocumentLoadState.Failed(it.message.orEmpty()) },
            )
    }
    var layout by remember(document.id) { mutableStateOf(SheetReaderLayout.ContinuousScroll) }
    var pageLabel by remember(document.id) { mutableStateOf<String?>(null) }
    var layoutMenuOpen by remember { mutableStateOf(false) }

    SongbookKeepScreenOnEffect()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = colors.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = document.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.common_done))
                        }
                    },
                    actions = {
                        pageLabel?.let {
                            Text(
                                text = it,
                                style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textTertiary,
                                modifier = Modifier.padding(horizontal = CzSpacing.xs),
                            )
                        }
                        if (loadState is DocumentLoadState.Loaded) {
                            Box {
                                IconButton(onClick = { layoutMenuOpen = true }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Description,
                                        contentDescription = stringResource(R.string.songbook_sheet_layout),
                                    )
                                }
                                DropdownMenu(
                                    expanded = layoutMenuOpen,
                                    onDismissRequest = { layoutMenuOpen = false },
                                ) {
                                    SheetReaderLayout.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(option.titleRes)) },
                                            onClick = {
                                                layout = option
                                                layoutMenuOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = {
                                    val file = (loadState as DocumentLoadState.Loaded).file
                                    shareSongDocument(context, file, document.kind, document.title)
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = stringResource(R.string.songbook_share_sheet_pdf),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.background,
                        scrolledContainerColor = colors.background,
                    ),
                    windowInsets = WindowInsets.safeDrawing,
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when (val state = loadState) {
                    DocumentLoadState.Loading -> CzLoadingView(
                        modifier = Modifier.align(Alignment.Center),
                        message = stringResource(R.string.songbook_loading_sheet),
                    )
                    is DocumentLoadState.Failed -> CzErrorState(
                        title = stringResource(R.string.songbook_document_load_error_title),
                        message = state.message.ifBlank { stringResource(R.string.songbook_sheet_load_error_message) },
                        retryLabel = stringResource(R.string.common_retry),
                        onRetry = { retryNonce++ },
                        modifier = Modifier.align(Alignment.Center),
                    )
                    is DocumentLoadState.Loaded -> PdfReaderContent(
                        file = state.file,
                        layout = layout,
                        onPageLabelChange = { pageLabel = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
fun SongLyricsPresenterDialog(
    song: Song,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.czColors
    val slides = remember(song) { SongPresentationDeckBuilder.deck(song) }
    val pagerState = rememberPagerState(pageCount = { slides.size.coerceAtLeast(1) })
    var showsControls by remember { mutableStateOf(true) }
    val pageLabel = if (slides.size > 1) "${pagerState.currentPage + 1} / ${slides.size}" else null

    SongbookKeepScreenOnEffect()
    SongbookPresentationOrientationEffect()
    SongbookFullScreenSystemBarsEffect(enabled = !showsControls)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures {
                        showsControls = !showsControls
                    }
                },
        ) {
            if (slides.isEmpty()) {
                CzEmptyState(
                    title = stringResource(R.string.songbook_nothing_to_present),
                    message = stringResource(R.string.songbook_nothing_to_present_message),
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    PresentationSlideView(
                        slide = slides[page],
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            PresentationControlBar(
                visible = showsControls,
                title = song.title,
                pageLabel = pageLabel,
                onDismiss = onDismiss,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun SongPptxPresenterDialog(
    document: SongDocumentItem,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.czColors
    val downloader = remember(context) { SongDocumentDownloader(context) }
    var retryNonce by remember(document.id) { mutableStateOf(0) }
    val loadState by produceState<DocumentLoadState>(DocumentLoadState.Loading, document.id, retryNonce) {
        value = DocumentLoadState.Loading
        value = runCatching { downloader.localFile(document.remoteUrl, document.kind) }
            .fold(
                onSuccess = DocumentLoadState::Loaded,
                onFailure = { DocumentLoadState.Failed(it.message.orEmpty()) },
            )
    }
    var showsControls by remember { mutableStateOf(true) }
    var webFailed by remember(document.id) { mutableStateOf(false) }

    SongbookKeepScreenOnEffect()
    SongbookPresentationOrientationEffect()
    SongbookFullScreenSystemBarsEffect(enabled = !showsControls)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures { showsControls = !showsControls }
                },
        ) {
            when (val state = loadState) {
                DocumentLoadState.Loading -> CzLoadingView(
                    modifier = Modifier.align(Alignment.Center),
                    message = stringResource(R.string.songbook_preparing_presentation),
                    contentDescription = stringResource(R.string.songbook_preparing_presentation),
                )
                is DocumentLoadState.Failed -> CzErrorState(
                    title = stringResource(R.string.songbook_document_load_error_title),
                    message = state.message.ifBlank { stringResource(R.string.songbook_slides_load_error_message) },
                    retryLabel = stringResource(R.string.common_retry),
                    onRetry = { retryNonce++ },
                    modifier = Modifier.align(Alignment.Center),
                )
                is DocumentLoadState.Loaded -> {
                    if (webFailed) {
                        PptxFallbackState(
                            file = state.file,
                            document = document,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        PptxWebPreview(
                            remoteUrl = document.remoteUrl,
                            onFailed = { webFailed = true },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    PresentationControlBar(
                        visible = showsControls,
                        title = document.title,
                        pageLabel = null,
                        onDismiss = onDismiss,
                        actions = {
                            IconButton(onClick = { shareSongDocument(context, state.file, document.kind, document.title) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = stringResource(R.string.common_share),
                                    tint = colors.cream,
                                )
                            }
                            IconButton(onClick = { openSongDocument(context, state.file, document.kind) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = stringResource(R.string.songbook_open_with),
                                    tint = colors.cream,
                                )
                            }
                        },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfReaderContent(
    file: File,
    layout: SheetReaderLayout,
    onPageLabelChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadataState by produceState<PdfMetadata?>(null, file.absolutePath) {
        value = withContext(Dispatchers.IO) { runCatching { readPdfMetadata(file) }.getOrNull() }
    }
    val metadata = metadataState

    if (metadata == null) {
        CzLoadingView(
            modifier = modifier,
            message = stringResource(R.string.songbook_loading_sheet),
        )
        return
    }

    if (metadata.pageCount == 0) {
        LaunchedEffect(Unit) { onPageLabelChange(null) }
        CzEmptyState(
            title = stringResource(R.string.songbook_no_sheet),
            message = stringResource(R.string.songbook_no_sheet_message),
            modifier = modifier,
        )
        return
    }

    when (layout) {
        SheetReaderLayout.ContinuousScroll -> ContinuousPdfReader(
            file = file,
            metadata = metadata,
            onPageLabelChange = onPageLabelChange,
            modifier = modifier,
        )
        SheetReaderLayout.Pages -> PagedPdfReader(
            file = file,
            metadata = metadata,
            onPageLabelChange = onPageLabelChange,
            modifier = modifier,
        )
    }
}

@Composable
private fun ContinuousPdfReader(
    file: File,
    metadata: PdfMetadata,
    onPageLabelChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentPage by remember(metadata.pageCount) {
        derivedStateOf { listState.firstVisibleItemIndex.coerceIn(0, metadata.pageCount - 1) }
    }

    LaunchedEffect(currentPage, metadata.pageCount) {
        onPageLabelChange(pageLabel(currentPage, metadata.pageCount))
    }

    LazyColumn(
        state = listState,
        modifier = modifier.background(Color(0xFF1D1D1D)),
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
        contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
    ) {
        items(metadata.pageCount) { pageIndex ->
            PdfPageImage(
                file = file,
                page = metadata.pages[pageIndex],
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CzSpacing.sm),
            )
        }
    }
}

@Composable
private fun PagedPdfReader(
    file: File,
    metadata: PdfMetadata,
    onPageLabelChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { metadata.pageCount })
    LaunchedEffect(pagerState.currentPage, metadata.pageCount) {
        onPageLabelChange(pageLabel(pagerState.currentPage, metadata.pageCount))
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.background(Color(0xFF1D1D1D)),
    ) { pageIndex ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(CzSpacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            PdfPageImage(
                file = file,
                page = metadata.pages[pageIndex],
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PdfPageImage(
    file: File,
    page: PdfPageInfo,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val targetWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val bitmapState by produceState<Bitmap?>(null, file.absolutePath, page.index, targetWidthPx) {
            value = withContext(Dispatchers.IO) {
                runCatching { renderPdfPage(file, page.index, targetWidthPx) }.getOrNull()
            }
        }
        val aspect = page.width.toFloat() / page.height.toFloat().coerceAtLeast(1f)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .shadow(8.dp, RoundedCornerShape(2.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = bitmapState
            if (bitmap == null) {
                CzLoadingView(message = null)
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.songbook_sheet_page, page.index + 1),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun PresentationControlBar(
    visible: Boolean,
    title: String,
    pageLabel: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = MaterialTheme.czColors
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.68f), Color.Transparent),
                    ),
                )
                .padding(WindowInsets.safeDrawing.asPaddingValues())
                .padding(horizontal = CzSpacing.base, vertical = CzSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(CzSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.songbook_end_presentation),
                    tint = colors.cream,
                )
            }
            Text(
                text = title,
                style = CzTypeScale.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = colors.cream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (pageLabel != null) {
                Text(
                    text = pageLabel,
                    style = CzTypeScale.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.cream,
                    modifier = Modifier
                        .clip(RoundedCornerShape(CzRadius.full))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = CzSpacing.sm, vertical = CzSpacing.xs),
                )
            }
            actions()
        }
    }
}

@Composable
private fun PresentationSlideView(
    slide: SongPresentationSlide,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.czColors
    val title = slide.kind as? SongPresentationSlideKind.Title
    val label = (slide.kind as? SongPresentationSlideKind.Lyrics)?.label
    val combinedLines = slide.lines.joinToString("\n")
    val locale = LocalLocale.current.platformLocale

    Column(
        modifier = modifier
            .padding(horizontal = CzSpacing.xxl, vertical = CzSpacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title != null) {
            Text(
                text = combinedLines,
                color = colors.cream,
                style = CzTypeScale.largeTitle.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = presentationFontSize(combinedLines, 56, 34).sp,
                ),
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
            if (title.subtitle.isNotBlank()) {
                Spacer(Modifier.height(CzSpacing.lg))
                Text(
                    text = title.subtitle,
                    color = colors.gold,
                    fontFamily = FontFamily.Serif,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            if (label != null) {
                Text(
                    text = label.uppercase(locale),
                    style = CzTypeScale.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.gold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(CzSpacing.lg))
            }
            Text(
                text = combinedLines,
                color = colors.cream,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                fontSize = presentationFontSize(combinedLines, 42, 24).sp,
                lineHeight = (presentationFontSize(combinedLines, 42, 24) * 1.18f).sp,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PptxWebPreview(
    remoteUrl: String,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewerUrl = remember(remoteUrl) {
        val encoded = URLEncoder.encode(remoteUrl, Charsets.UTF_8.name())
        "https://docs.google.com/gview?embedded=1&url=$encoded"
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                setBackgroundColor(android.graphics.Color.BLACK)
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.isForMainFrame) onFailed()
                    }
                }
            }
        },
        update = { webView ->
            if (webView.url != viewerUrl) {
                webView.loadUrl(viewerUrl)
            }
        },
    )
}

@Composable
private fun PptxFallbackState(
    file: File,
    document: SongDocumentItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.czColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(CzSpacing.xl)
            .clip(RoundedCornerShape(CzRadius.xl))
            .background(Color.Black.copy(alpha = 0.56f))
            .border(1.dp, colors.cream.copy(alpha = 0.22f), RoundedCornerShape(CzRadius.xl))
            .padding(CzSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CzSpacing.md),
    ) {
        Icon(
            imageVector = Icons.Rounded.Description,
            contentDescription = null,
            tint = colors.gold,
            modifier = Modifier.size(42.dp),
        )
        Text(
            text = stringResource(R.string.songbook_pptx_preview_unavailable),
            color = colors.cream,
            style = CzTypeScale.title3.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.songbook_pptx_preview_unavailable_message),
            color = colors.cream.copy(alpha = 0.74f),
            style = CzTypeScale.body,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(CzSpacing.sm)) {
            CzButton(
                text = stringResource(R.string.common_share),
                onClick = { shareSongDocument(context, file, document.kind, document.title) },
                variant = CzButtonVariant.Outline,
            )
            CzButton(
                text = stringResource(R.string.songbook_open_with),
                onClick = { openSongDocument(context, file, document.kind) },
                variant = CzButtonVariant.Primary,
            )
        }
    }
}

@Composable
private fun SongbookKeepScreenOnEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        view.context.findActivity()?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            view.keepScreenOn = false
            view.context.findActivity()?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
private fun SongbookFullScreenSystemBarsEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled, view) {
        if (!enabled) {
            onDispose {}
        } else {
            val window = view.context.findActivity()?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val previousBehavior = controller?.systemBarsBehavior
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                if (previousBehavior != null) controller.systemBarsBehavior = previousBehavior
            }
        }
    }
}

@Composable
private fun SongbookPresentationOrientationEffect() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        if (activity == null) {
            onDispose {}
        } else {
            val previous = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose {
                activity.requestedOrientation = previous
            }
        }
    }
}

private fun shareSongDocument(
    context: Context,
    file: File,
    kind: SongDocumentKind,
    title: String,
) {
    val uri = file.contentUri(context)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = kind.mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.common_share)))
    }.onFailure { error ->
        if (error is ActivityNotFoundException) {
            Toast.makeText(context, R.string.songbook_no_document_app, Toast.LENGTH_SHORT).show()
        } else {
            throw error
        }
    }
}

private fun openSongDocument(
    context: Context,
    file: File,
    kind: SongDocumentKind,
) {
    val uri = file.contentUri(context)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, kind.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.songbook_open_with)))
    }.onFailure { error ->
        if (error is ActivityNotFoundException) {
            Toast.makeText(context, R.string.songbook_no_document_app, Toast.LENGTH_SHORT).show()
        } else {
            throw error
        }
    }
}

private fun File.contentUri(context: Context): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this)

private fun pageLabel(pageIndex: Int, count: Int): String? =
    if (count > 1) "${pageIndex + 1}/$count" else null

private fun presentationFontSize(text: String, base: Int, min: Int): Int {
    val lines = text.lines().size.coerceAtLeast(1)
    val longest = text.lines().maxOfOrNull { it.length } ?: 0
    val linePenalty = ((lines - 4).coerceAtLeast(0) * 3)
    val lengthPenalty = ((longest - 34).coerceAtLeast(0) / 3)
    return (base - linePenalty - lengthPenalty).coerceAtLeast(min)
}

private fun readPdfMetadata(file: File): PdfMetadata {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val pages = (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { page ->
                    PdfPageInfo(index = index, width = page.width.coerceAtLeast(1), height = page.height.coerceAtLeast(1))
                }
            }
            return PdfMetadata(pageCount = renderer.pageCount, pages = pages)
        }
    }
}

private fun renderPdfPage(file: File, pageIndex: Int, targetWidthPx: Int): Bitmap {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(pageIndex).use { page ->
                val ratio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                val height = (targetWidthPx * ratio).roundToInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(targetWidthPx, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private sealed interface DocumentLoadState {
    data object Loading : DocumentLoadState
    data class Loaded(val file: File) : DocumentLoadState
    data class Failed(val message: String) : DocumentLoadState
}

private enum class SheetReaderLayout(val titleRes: Int) {
    ContinuousScroll(R.string.songbook_sheet_layout_scroll),
    Pages(R.string.songbook_sheet_layout_pages),
}

private data class PdfMetadata(
    val pageCount: Int,
    val pages: List<PdfPageInfo>,
)

private data class PdfPageInfo(
    val index: Int,
    val width: Int,
    val height: Int,
)
