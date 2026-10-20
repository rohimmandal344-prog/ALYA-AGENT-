package com.example.data.model

data class AlyaLanguage(
    val code: String,
    val name: String,
    val country: String,
    val flag: String
) {
    val displayLabel: String get() = "$flag $name ($country)"
}
