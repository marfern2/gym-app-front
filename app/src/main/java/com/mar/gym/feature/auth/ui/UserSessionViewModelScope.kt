package com.mar.gym.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/** Owns every ViewModel whose state belongs to the currently authenticated user. */
class UserSessionViewModelScope : ViewModel(), ViewModelStoreOwner {
    private var activeUserId: String? = null
    private var store = ViewModelStore()

    override val viewModelStore: ViewModelStore
        get() = store

    /** Returns true when an existing user's scope was destroyed. */
    fun activate(userId: String): Boolean {
        require(userId.isNotBlank())
        if (activeUserId == userId) return false
        val replacedExistingUser = activeUserId != null
        store.clear()
        store = ViewModelStore()
        activeUserId = userId
        return replacedExistingUser
    }

    fun clearSession() {
        if (activeUserId == null) return
        activeUserId = null
        store.clear()
        store = ViewModelStore()
    }

    override fun onCleared() {
        activeUserId = null
        store.clear()
    }
}
