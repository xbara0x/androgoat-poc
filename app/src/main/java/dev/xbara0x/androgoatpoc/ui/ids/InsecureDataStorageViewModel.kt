package dev.xbara0x.androgoatpoc.ui.ids

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class InsecureDataStorageViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "Insecure Data Storage – SD Card"
    }
    val text: LiveData<String> = _text
}