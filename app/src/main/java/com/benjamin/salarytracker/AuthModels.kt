package com.benjamin.salarytracker

data class UserSession(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    /** true = compte local : données stockées sur l'appareil, pas sur Firebase. */
    val isLocal: Boolean = false
)
