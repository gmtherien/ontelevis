package app.ontelevis.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.ontelevis.core.designsystem.R

// Fonts bundled in res/font (see res/font/README.md). Inter + Inter Tight, OFL.
val Inter =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )
val InterTight =
    FontFamily(
        Font(R.font.intertight_medium, FontWeight.Medium),
        Font(R.font.intertight_semibold, FontWeight.SemiBold),
        Font(R.font.intertight_bold, FontWeight.Bold),
    )

private const val TABULAR = "tnum"

/** All sizes in sp for a 960×540 dp TV canvas. Minimum readable text at 3 m: 11 sp. */
@Immutable
data class OntTypography(
    /** Channel title in the info panel. */
    val display: TextStyle =
        TextStyle(
            fontFamily = InterTight,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 31.sp,
            letterSpacing = (-0.015).em,
        ),
    /** Section title ("Télé"). */
    val title: TextStyle =
        TextStyle(
            fontFamily = InterTight,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            letterSpacing = (-0.01).em,
        ),
    /** Header clock. */
    val clock: TextStyle =
        TextStyle(
            fontFamily = InterTight,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 23.sp,
            fontFeatureSettings = TABULAR,
        ),
    /** Breadcrumb, list items, button labels. */
    val item: TextStyle =
        TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 18.sp),
    /** Emphasised item: channel name, program title. */
    val itemStrong: TextStyle =
        TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 17.sp),
    /** Body / description. */
    val body: TextStyle =
        TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    /** Secondary metadata ("Relax · HDR"). */
    val meta: TextStyle =
        TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 15.sp),
    /** EPG header ticks, channel numbers, program times. */
    val time: TextStyle =
        TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontFeatureSettings = TABULAR,
        ),
    /** Uppercase section label: pass the text already uppercased. */
    val sectionLabel: TextStyle =
        TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 0.08.em,
        ),
    /** Tags: 4K, HDR, PPV, 24/7, EN DIRECT. */
    val tag: TextStyle =
        TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            letterSpacing = 0.04.em,
        ),
    /** Primary button label. */
    val button: TextStyle =
        TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp),
    /** Brand mark letter in the rail logo. */
    val logo: TextStyle =
        TextStyle(fontFamily = InterTight, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 20.sp),
)

val LocalOntTypography = staticCompositionLocalOf { OntTypography() }
