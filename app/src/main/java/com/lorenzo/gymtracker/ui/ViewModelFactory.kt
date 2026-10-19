package com.lorenzo.gymtracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lorenzo.gymtracker.AppContainer
import com.lorenzo.gymtracker.GymTrackerApp

/** Builds a ViewModel factory that has access to the app's [AppContainer]. */
inline fun <reified VM : ViewModel> appViewModelFactory(crossinline create: (AppContainer) -> VM): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { create((this[APPLICATION_KEY] as GymTrackerApp).container) }
    }
