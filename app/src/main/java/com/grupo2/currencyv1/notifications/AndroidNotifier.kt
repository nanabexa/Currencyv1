package com.grupo2.currencyv1.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.grupo2.currencyv1.MainActivity
import com.grupo2.currencyv1.domain.NotificationType

/** Notificaciones locales de Android. No usa internet (RNF-05). */
class AndroidNotifier(private val context: Context) : Notifier {

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Avisos del presupuesto",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Presupuesto bajo, excedentes y descuentos de gastos fijos" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Desde Android 13 hace falta el permiso POST_NOTIFICATIONS, y el usuario puede desactivar el canal. */
    private fun canNotify(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission") // el permiso se comprueba en canNotify()
    override fun show(kind: NotificationType, id: Int, title: String, message: String): Boolean {
        if (!canNotify()) return false

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()

        // El tag (tipo de aviso) + el id evitan que unos avisos se pisen con otros.
        NotificationManagerCompat.from(context).notify(kind.name, id, notification)
        return true
    }

    private companion object {
        const val CHANNEL_ID = "budget_alerts"
    }
}
