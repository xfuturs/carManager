package com.carmanager.app.core.domain.model

/** Ready porte un snapshot réel, y compris une liste réellement vide. */
sealed interface LocalDataState<out T> {
    data object Loading : LocalDataState<Nothing>
    data class Ready<T>(val owner: String, val data: T) : LocalDataState<T>
    data class Error(val owner: String) : LocalDataState<Nothing>
}

fun <T> LocalDataState<T>.forOwner(owner: String): LocalDataState<T> = when (this) {
    is LocalDataState.Ready -> if (this.owner == owner) this else LocalDataState.Loading
    is LocalDataState.Error -> if (this.owner == owner) this else LocalDataState.Loading
    LocalDataState.Loading -> this
}
