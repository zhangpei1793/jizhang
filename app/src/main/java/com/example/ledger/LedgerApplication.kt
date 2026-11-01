package com.example.ledger

import android.app.Application
import com.example.ledger.di.AppContainer

class LedgerApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
}
