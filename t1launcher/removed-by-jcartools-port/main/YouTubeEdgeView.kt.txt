package com.ghosted907.t1launcher

import android.content.Context

/** Mirrored counterpart, sharing the right launcher's exact pull behavior and renderer. */
internal class YouTubeEdgeView(context: Context) : EdgePullView(context, fromRight = false)