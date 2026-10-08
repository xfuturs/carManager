package com.carmanager.app.core.domain.session

import com.carmanager.app.core.domain.model.LocalDataState
import com.carmanager.app.core.domain.model.forOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

/** Réinitialise au changement d'espace/retry, sans inventer un snapshot vide. */
@OptIn(ExperimentalCoroutinesApi::class)
fun <T> observeLocalState(
    session: WorkspaceSession,
    retry: Flow<Int>,
    query: (String) -> Flow<T>
): Flow<LocalDataState<T>> = combine(session.owner, session.readiness, retry) { owner, readiness, attempt ->
    Triple(owner, readiness, attempt)
}.flatMapLatest { (owner, readiness, _) ->
    flow<LocalDataState<T>> {
        emit(LocalDataState.Loading)
        if (readiness == GarageReadiness.Error) emit(LocalDataState.Error(owner))
        if (readiness == GarageReadiness.Ready) {
            try {
                query(owner).collect { data ->
                    if (session.owner.value == owner) emit(LocalDataState.Ready(owner, data))
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                emit(LocalDataState.Error(owner))
            }
        }
    }
}.combine(session.owner) { state, owner -> state.forOwner(owner) }.distinctUntilChanged()
