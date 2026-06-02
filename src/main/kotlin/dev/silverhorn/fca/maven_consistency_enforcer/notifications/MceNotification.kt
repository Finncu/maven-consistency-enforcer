package dev.silverhorn.fca.maven_consistency_enforcer.notifications

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import dev.silverhorn.fca.maven_consistency_enforcer.EnforcerBundle

/**
 * MceNotification: Zentralisiertes Notification-System für MCE.
 *
 * Verwaltet alle Benutzer-Benachrichtigungen des Maven Consistency Enforcer Plugins.
 * Alle Notifications werden durch die Notification-Gruppe "Maven Consistency Enforcer"
 * kanalisiert, was dem Benutzer ermöglicht, (de)aktiviert zu werden.
 *
 * **Notification-Typen:**
 * - INFO: Routine-Informationen (z.B. "3 dependencies fixed")
 * - WARNING: Potenzielle Probleme (z.B. "Module not found")
 * - ERROR: Kritische Fehler (z.B. "Failed to update model")
 */
object MceNotification {

    private const val GROUP_ID = "Maven Consistency Enforcer"

    /**
     * Gibt die in plugin.xml deklarierte NotificationGroup für MCE zurück.
     *
     * @return Die NotificationGroup für MCE
     */
    private fun getNotificationGroup(): NotificationGroup {
        return NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
    }

    /**
     * Zeigt eine Info-Notification an.
     *
     * Wird verwendet für Routine-Meldungen wie Erfolgs-Reports oder
     * Status-Updates. Diese Notifications werden automatisch nach kurzer Zeit ausgeblendet.
     *
     * @param project Das aktuelle Projekt
     * @param message Die anzuzeigende Nachricht
     * @param title Optionaler Titel (Standard: "Maven Consistency Enforcer")
     */
    fun showInfo(project: Project, message: String, title: String = EnforcerBundle.message("notification.title")) {
        val notification = getNotificationGroup()
            .createNotification(title, message, NotificationType.INFORMATION)
        notification.notify(project)
    }

    /**
     * Zeigt eine Warnungs-Notification an.
     *
     * Wird verwendet für potenzielle Probleme, die zwar nicht kritisch sind,
     * aber die Aufmerksamkeit des Benutzers verdienen. Diese Notifications
     * verschwinden nicht automatisch.
     *
     * @param project Das aktuelle Projekt
     * @param message Die anzuzeigende Nachricht
     * @param title Optionaler Titel (Standard: "Maven Consistency Enforcer")
     */
    fun showWarning(project: Project, message: String, title: String = EnforcerBundle.message("notification.title")) {
        val notification = getNotificationGroup()
            .createNotification(title, message, NotificationType.WARNING)
        notification.notify(project)
    }

    /**
     * Zeigt eine Fehler-Notification an.
     *
     * Wird verwendet für kritische Fehler, die manuelles Eingreifen erfordern könnten.
     * Diese Notifications sind optisch prominent und verschwinden nicht automatisch.
     *
     * @param project Das aktuelle Projekt
     * @param message Die anzuzeigende Fehlermeldung
     * @param title Optionaler Titel (Standard: "Maven Consistency Enforcer")
     */
    fun showError(project: Project, message: String, title: String = EnforcerBundle.message("notification.title")) {
        val notification = getNotificationGroup()
            .createNotification(title, message, NotificationType.ERROR)
        notification.notify(project)
    }
}
