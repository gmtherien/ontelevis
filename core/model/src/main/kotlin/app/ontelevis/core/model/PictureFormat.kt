package app.ontelevis.core.model

/** How the picture fills the screen. */
enum class PictureFormat {
    /** Whole picture, black bars when the shapes differ. */
    FIT,

    /** Stretched to the whole screen. */
    FILL,

    /** Whole screen, the edges of the picture cut. */
    ZOOM,
}
