package dev.xbara0x.androgoatpoc.ui.provider

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ProviderLeakViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Exported ContentProvider – AndroGoat user pins"
    }
    val text: LiveData<String> = _text
}