package com.dccleaner.app.model

object DeleteTaskStartValidator {
    fun hasCompleteGalleryMap(
        selectedGalleries: List<String>,
        galleryMap: Map<String, String>
    ): Boolean = selectedGalleries.isNotEmpty() &&
            selectedGalleries.all { galleryId ->
                !galleryMap[galleryId].isNullOrBlank()
            }

    fun selectedGalleryMap(
        selectedGalleries: List<String>,
        galleryMap: Map<String, String>
    ): Map<String, String> = selectedGalleries.associateWith { galleryId ->
        galleryMap.getValue(galleryId)
    }

    fun retainAvailableSelection(
        selectedGalleries: List<String>,
        galleryMap: Map<String, String>
    ): List<String> = selectedGalleries.filter(galleryMap::containsKey)
}
