package com.scanlist.app.ui.cardtest

import android.nfc.Tag
import androidx.lifecycle.ViewModel
import com.scanlist.app.scanner.CardTestOutcome
import com.scanlist.app.scanner.CardTestReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed interface CardTestState {
    data object Waiting : CardTestState

    data object Running : CardTestState

    class Done(val outcome: CardTestOutcome) : CardTestState
}

@HiltViewModel
class CardTestViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow<CardTestState>(CardTestState.Waiting)
    val state: StateFlow<CardTestState> = _state.asStateFlow()

    /**
     * Appele depuis le rappel du lecteur NFC, hors du thread principal, et bloquant le
     * temps du dialogue avec le badge. compareAndSet : le rappel NFC et l'ecran ne
     * partagent pas de thread, et un badge represente pendant un test en cours doit
     * etre ignore plutot que de lancer un second dialogue concurrent.
     */
    fun onTag(tag: Tag) {
        val current = _state.value
        if (current == CardTestState.Running) return
        if (!_state.compareAndSet(current, CardTestState.Running)) return
        _state.value = CardTestState.Done(CardTestReader.read(tag))
    }

    fun reset() {
        val current = _state.value
        if (current is CardTestState.Done) _state.compareAndSet(current, CardTestState.Waiting)
    }
}
