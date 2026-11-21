package com.admin.siliconpulse

import android.app.Application
import android.content.Context
import com.admin.siliconpulse.data.audit.AppAuditRepository
import com.admin.siliconpulse.data.export.ReportExporter
import com.admin.siliconpulse.ui.ViewModePrefs
import com.admin.siliconpulse.data.power.PowerRepositoryImpl
import com.admin.siliconpulse.data.privacy.PrivacyRepositoryImpl
import com.admin.siliconpulse.data.radios.RadioRepositoryImpl
import com.admin.siliconpulse.data.sensors.SensorRepositoryImpl
import com.admin.siliconpulse.domain.repository.PowerRepository
import com.admin.siliconpulse.domain.repository.PrivacyRepository
import com.admin.siliconpulse.domain.repository.RadioRepository
import com.admin.siliconpulse.domain.repository.SensorRepository

class AppContainer(context: Context) {
    val sensors: SensorRepository by lazy { SensorRepositoryImpl(context) }
    val privacy: PrivacyRepository by lazy { PrivacyRepositoryImpl(context) }
    val radios: RadioRepository by lazy { RadioRepositoryImpl(context) }
    val power: PowerRepository by lazy { PowerRepositoryImpl(context) }
    val exporter: ReportExporter by lazy { ReportExporter(context) }
    val audit: AppAuditRepository by lazy { AppAuditRepository(context) }
    val viewMode: ViewModePrefs by lazy { ViewModePrefs(context) }
}

class SiliconPulseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as SiliconPulseApp).container
