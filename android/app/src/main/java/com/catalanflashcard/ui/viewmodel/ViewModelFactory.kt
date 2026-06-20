package com.catalanflashcard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Generic factory that builds a [ViewModel] from the supplied lambda, avoiding a
 * separate boilerplate factory class per ViewModel.
 */
class ViewModelFactory<out VM : ViewModel>(
    private val create: () -> VM
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
