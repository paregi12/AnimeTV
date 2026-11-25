package com.paregi.animetv.features.profile

internal data class WallpaperChoice(
    val source: String,
    val title: String,
    val drawable: String? = null,
    val thumbnail: String? = null,
    val imageUrl: String? = null
)

/** The original 41 curated wallpapers are bundled locally, so no legacy host is needed. */
internal val curatedWallpapers = listOf(
    WallpaperChoice("1.webp", "Gate", "wallpaper_gallery_1", "wallpaper_thumb_1"),
    WallpaperChoice("2.jpg", "Waifu 1", "wallpaper_gallery_2", "wallpaper_thumb_2"),
    WallpaperChoice("3.jpg", "Sunny Go", "wallpaper_gallery_3", "wallpaper_thumb_3"),
    WallpaperChoice("4.jpg", "Waifu 2", "wallpaper_gallery_4", "wallpaper_thumb_4"),
    WallpaperChoice("5.webp", "Your Name", "wallpaper_gallery_5", "wallpaper_thumb_5"),
    WallpaperChoice("6.jpg", "Fuji", "wallpaper_gallery_6", "wallpaper_thumb_6"),
    WallpaperChoice("7.jpg", "Your Name 2", "wallpaper_gallery_7", "wallpaper_thumb_7"),
    WallpaperChoice("8.webp", "Hill Bench", "wallpaper_gallery_8", "wallpaper_thumb_8"),
    WallpaperChoice("9.webp", "Gojo Satoru", "wallpaper_gallery_9", "wallpaper_thumb_9"),
    WallpaperChoice("10.jpg", "Spike Spiegel", "wallpaper_gallery_10", "wallpaper_thumb_10"),
    WallpaperChoice("11.jpg", "Land of the lustrous", "wallpaper_gallery_11", "wallpaper_thumb_11"),
    WallpaperChoice("12.jpg", "Naruto and Minato", "wallpaper_gallery_12", "wallpaper_thumb_12"),
    WallpaperChoice("13.jpg", "Makima", "wallpaper_gallery_13", "wallpaper_thumb_13"),
    WallpaperChoice("14.jpg", "Eren", "wallpaper_gallery_14", "wallpaper_thumb_14"),
    WallpaperChoice("15.jpg", "Gojo Satoru 2", "wallpaper_gallery_15", "wallpaper_thumb_15"),
    WallpaperChoice("16.jpg", "Ken", "wallpaper_gallery_16", "wallpaper_thumb_16"),
    WallpaperChoice("17.jpg", "L", "wallpaper_gallery_17", "wallpaper_thumb_17"),
    WallpaperChoice("18.jpg", "Miyamoto", "wallpaper_gallery_18", "wallpaper_thumb_18"),
    WallpaperChoice("19.jpg", "Tanjiro", "wallpaper_gallery_19", "wallpaper_thumb_19"),
    WallpaperChoice("20.jpg", "Dante", "wallpaper_gallery_20", "wallpaper_thumb_20"),
    WallpaperChoice("21.jpg", "Guts", "wallpaper_gallery_21", "wallpaper_thumb_21"),
    WallpaperChoice("22.jpg", "Mikasa", "wallpaper_gallery_22", "wallpaper_thumb_22"),
    WallpaperChoice("23.jpg", "Spike Spiegel 2", "wallpaper_gallery_23", "wallpaper_thumb_23"),
    WallpaperChoice("24.jpg", "Sukuna", "wallpaper_gallery_24", "wallpaper_thumb_24"),
    WallpaperChoice("25.jpg", "Ash Ketchum", "wallpaper_gallery_25", "wallpaper_thumb_25"),
    WallpaperChoice("26.jpg", "Goku", "wallpaper_gallery_26", "wallpaper_thumb_26"),
    WallpaperChoice("27.jpg", "Gon", "wallpaper_gallery_27", "wallpaper_thumb_27"),
    WallpaperChoice("28.jpg", "Killua", "wallpaper_gallery_28", "wallpaper_thumb_28"),
    WallpaperChoice("29.jpg", "Levi Ackerman", "wallpaper_gallery_29", "wallpaper_thumb_29"),
    WallpaperChoice("30.jpg", "Luffy", "wallpaper_gallery_30", "wallpaper_thumb_30"),
    WallpaperChoice("31.jpg", "Motoko Kusanagi", "wallpaper_gallery_31", "wallpaper_thumb_31"),
    WallpaperChoice("32.jpg", "Saitama", "wallpaper_gallery_32", "wallpaper_thumb_32"),
    WallpaperChoice("33.jpg", "Zoro", "wallpaper_gallery_33", "wallpaper_thumb_33"),
    WallpaperChoice("34.jpg", "Ace", "wallpaper_gallery_34", "wallpaper_thumb_34"),
    WallpaperChoice("35.jpg", "Gojo and Geto", "wallpaper_gallery_35", "wallpaper_thumb_35"),
    WallpaperChoice("36.jpg", "Nami", "wallpaper_gallery_36", "wallpaper_thumb_36"),
    WallpaperChoice("37.jpg", "Kirito", "wallpaper_gallery_37", "wallpaper_thumb_37"),
    WallpaperChoice("38.jpg", "Revy", "wallpaper_gallery_38", "wallpaper_thumb_38"),
    WallpaperChoice("39.jpg", "Akame", "wallpaper_gallery_39", "wallpaper_thumb_39"),
    WallpaperChoice("40.jpg", "Shigeo", "wallpaper_gallery_40", "wallpaper_thumb_40"),
    WallpaperChoice("41.jpg", "Future trunks", "wallpaper_gallery_41", "wallpaper_thumb_41")
)

