package com.sorcerer.obelisk.notif

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.sorcerer.obelisk.R

class NotificationAppFilterActivity : AppCompatActivity() {

    private data class AppEntry(val label: String, val packageName: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_filter)

        val listView = findViewById<ListView>(R.id.appList)
        val saveButton = findViewById<Button>(R.id.saveButton)

        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { app ->
                // Keep apps a person would recognize: has a launcher icon,
                // or isn't a pure system component.
                pm.getLaunchIntentForPackage(app.packageName) != null ||
                        (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            }
            .map { AppEntry(pm.getApplicationLabel(it).toString(), it.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }

        val allowed = NotificationFilterStore.getAllowed(this)
        // Empty stored set means "everything allowed" - reflect that as
        // every checkbox starting checked.
        val everythingAllowed = allowed.isEmpty()

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_multiple_choice,
            apps.map { it.label }
        )
        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

        apps.forEachIndexed { index, app ->
            val checked = everythingAllowed || allowed.contains(app.packageName)
            listView.setItemChecked(index, checked)
        }

        saveButton.setOnClickListener {
            val checkedPackages = apps.filterIndexed { index, _ ->
                listView.checkedItemPositions.get(index, false)
            }.map { it.packageName }.toSet()

            // If literally everything is checked, store an empty set so the
            // semantics stay "forward all" rather than a giant explicit list.
            val toStore = if (checkedPackages.size == apps.size) emptySet() else checkedPackages
            NotificationFilterStore.setAllowed(this, toStore)
            finish()
        }
    }
}