package com.benjamin.salarytracker

data class UserSession(
    val uid: String,
    val displayName: String,
    val email: String,
    val photoUrl: String? = null,
    val isMock: Boolean = false
)
