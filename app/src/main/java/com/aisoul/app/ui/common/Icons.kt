package com.aisoul.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn 24dp stroke icons. No icon library: keeps the look ours and
 * the APK small. Stroke 1.75 at text-secondary reads quiet and precise.
 */
private fun icon(name: String, block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(block).build()

private fun androidx.compose.ui.graphics.vector.ImageVector.Builder.stroke(
    pathData: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
) {
    path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.75f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = pathData,
    )
}

object AiSoulIcons {

    val ArrowUp: ImageVector by lazy {
        icon("arrowUp") {
            stroke {
                moveTo(12f, 19f); lineTo(12f, 5f)
                moveTo(6f, 11f); lineTo(12f, 5f); lineTo(18f, 11f)
            }
        }
    }

    val Stop: ImageVector by lazy {
        icon("stop") {
            path(fill = SolidColor(Color.White)) {
                moveTo(8.5f, 8.5f)
                lineTo(15.5f, 8.5f)
                lineTo(15.5f, 15.5f)
                lineTo(8.5f, 15.5f)
                close()
            }
        }
    }

    val Plus: ImageVector by lazy {
        icon("plus") {
            stroke {
                moveTo(12f, 5f); lineTo(12f, 19f)
                moveTo(5f, 12f); lineTo(19f, 12f)
            }
        }
    }

    val Back: ImageVector by lazy {
        icon("back") {
            stroke {
                moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        icon("copy") {
            stroke {
                moveTo(9f, 9f); lineTo(19f, 9f); lineTo(19f, 19f); lineTo(9f, 19f); close()
                moveTo(5f, 15f); lineTo(5f, 5f); lineTo(15f, 5f)
            }
        }
    }

    val Check: ImageVector by lazy {
        icon("check") {
            stroke {
                moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f)
            }
        }
    }

    /** >_ prompt */
    val Terminal: ImageVector by lazy {
        icon("terminal") {
            stroke {
                moveTo(5f, 7f); lineTo(10f, 12f); lineTo(5f, 17f)
                moveTo(12f, 17f); lineTo(19f, 17f)
            }
        }
    }

    /** clock face */
    val History: ImageVector by lazy {
        icon("history") {
            stroke {
                moveTo(12f, 4f)
                curveTo(16.42f, 4f, 20f, 7.58f, 20f, 12f)
                curveTo(20f, 16.42f, 16.42f, 20f, 12f, 20f)
                curveTo(7.58f, 20f, 4f, 16.42f, 4f, 12f)
                curveTo(4f, 7.58f, 7.58f, 4f, 12f, 4f)
                moveTo(12f, 8f); lineTo(12f, 12f); lineTo(15f, 14f)
            }
        }
    }

    val Trash: ImageVector by lazy {
        icon("trash") {
            stroke {
                moveTo(5f, 7f); lineTo(19f, 7f)
                moveTo(9f, 7f); lineTo(9f, 4.5f); lineTo(15f, 4.5f); lineTo(15f, 7f)
                moveTo(7f, 7f); lineTo(7.8f, 19.5f); lineTo(16.2f, 19.5f); lineTo(17f, 7f)
            }
        }
    }

    /** four quiet squares — the dashboard */
    val Grid: ImageVector by lazy {
        icon("grid") {
            stroke {
                moveTo(5f, 5f); lineTo(10.5f, 5f); lineTo(10.5f, 10.5f); lineTo(5f, 10.5f); close()
                moveTo(13.5f, 5f); lineTo(19f, 5f); lineTo(19f, 10.5f); lineTo(13.5f, 10.5f); close()
                moveTo(5f, 13.5f); lineTo(10.5f, 13.5f); lineTo(10.5f, 19f); lineTo(5f, 19f); close()
                moveTo(13.5f, 13.5f); lineTo(19f, 13.5f); lineTo(19f, 19f); lineTo(13.5f, 19f); close()
            }
        }
    }

    val Chevron: ImageVector by lazy {
        icon("chevron") {
            stroke {
                moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
            }
        }
    }

    /** circular arrow — retry the last exchange */
    val Retry: ImageVector by lazy {
        icon("retry") {
            stroke {
                moveTo(19.2f, 12f)
                curveTo(19.2f, 16f, 16f, 19.2f, 12f, 19.2f)
                curveTo(8f, 19.2f, 4.8f, 16f, 4.8f, 12f)
                curveTo(4.8f, 8f, 8f, 4.8f, 12f, 4.8f)
                curveTo(14.4f, 4.8f, 16.5f, 5.9f, 17.8f, 7.7f)
                moveTo(18.2f, 3.8f); lineTo(18.2f, 8.2f); lineTo(13.8f, 8.2f)
            }
        }
    }

    /** small flag — report a response */
    val Flag: ImageVector by lazy {
        icon("flag") {
            stroke {
                moveTo(6f, 20f); lineTo(6f, 4.5f)
                moveTo(6f, 5f); lineTo(17.5f, 5f); lineTo(14.5f, 8.5f); lineTo(17.5f, 12f); lineTo(6f, 12f)
            }
        }
    }
}
