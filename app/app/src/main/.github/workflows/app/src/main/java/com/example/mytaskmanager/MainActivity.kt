package com.example.mytaskmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private data class TaskItem(
    val id: Long,
    val title: String,
    val page: String,
    val done: Boolean = false,
    val pinned: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TaskManagerApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskManagerApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("task_manager", 0) }
    var tasks by remember { mutableStateOf(loadTasks(prefs.getString("tasks", "[]") ?: "[]")) }
    var pages by remember { mutableStateOf(loadPages(prefs.getString("pages", "[\"My Tasks\"]") ?: "[\"My Tasks\"]")) }
    var selectedPage by remember { mutableStateOf("My Tasks") }
    var showPending by remember { mutableStateOf(false) }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    var searchText by remember { mutableStateOf("") }
    var newTaskText by remember { mutableStateOf("") }
    var newPageText by remember { mutableStateOf("") }
    var showAddPage by remember { mutableStateOf(false) }

    fun save() {
        prefs.edit()
            .putString("tasks", tasksToJson(tasks))
            .putString("pages", pagesToJson(pages))
            .putBoolean("dark", darkMode)
            .apply()
    }

    val colorScheme = if (darkMode) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("My Task Manager", fontWeight = FontWeight.Bold)
                            Text(
                                if (showPending) "All pending tasks" else selectedPage,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            darkMode = !darkMode
                            save()
                        }) {
                            Icon(
                                if (darkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Switch theme"
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = {
                    if (newTaskText.isNotBlank()) {
                        tasks = tasks + TaskItem(
                            id = System.currentTimeMillis(),
                            title = newTaskText.trim(),
                            page = if (showPending) selectedPage else selectedPage
                        )
                        newTaskText = ""
                        save()
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add task")
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !showPending,
                        onClick = { showPending = false },
                        label = { Text("Current page") },
                        leadingIcon = { Icon(Icons.Default.TaskAlt, null) }
                    )
                    FilterChip(
                        selected = showPending,
                        onClick = { showPending = true },
                        label = { Text("Pending") }
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showAddPage = true }) {
                        Icon(Icons.Default.Add, contentDescription = "New page")
                    }
                }

                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search tasks…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = newTaskText,
                    onValueChange = { newTaskText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Write a new task…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newTaskText.isNotBlank()) {
                            tasks = tasks + TaskItem(System.currentTimeMillis(), newTaskText.trim(), selectedPage)
                            newTaskText = ""
                            save()
                        }
                    })
                )
                Spacer(Modifier.height(8.dp))

                if (!showPending) {
                    Text("Pages", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        pages.forEach { page ->
                            FilterChip(
                                selected = selectedPage == page,
                                onClick = { selectedPage = page },
                                label = { Text(page, maxLines = 1) }
                            )
                        }
                    }
                }

                val visibleTasks = tasks
                    .filter { if (showPending) !it.done else it.page == selectedPage }
                    .filter { it.title.contains(searchText, ignoreCase = true) }
                    .sortedWith(compareByDescending<TaskItem> { it.pinned }.thenBy { it.done }.thenByDescending { it.id })

                Text(
                    "${visibleTasks.count { !it.done }} pending · ${visibleTasks.count { it.done }} completed",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium
                )

                if (visibleTasks.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (searchText.isNotBlank()) "No matching tasks" else "Nothing here yet. Add your first task!")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = true),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(visibleTasks, key = { it.id }) { task ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = task.done,
                                        onCheckedChange = { checked ->
                                            tasks = tasks.map { if (it.id == task.id) it.copy(done = checked) else it }
                                            save()
                                        }
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f).clickable {
                                            tasks = tasks.map { if (it.id == task.id) it.copy(done = !it.done) else it }
                                            save()
                                        }.padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            task.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (task.pinned) FontWeight.Bold else FontWeight.Normal,
                                            color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (showPending) {
                                            Text(task.page, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    IconButton(onClick = {
                                        tasks = tasks.map { if (it.id == task.id) it.copy(pinned = !it.pinned) else it }
                                        save()
                                    }) {
                                        Icon(
                                            Icons.Default.PushPin,
                                            contentDescription = "Pin task",
                                            tint = if (task.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = {
                                        tasks = tasks.filterNot { it.id == task.id }
                                        save()
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete task")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddPage) {
            AlertDialog(
                onDismissRequest = { showAddPage = false },
                title = { Text("Create a new page") },
                text = {
                    OutlinedTextField(
                        value = newPageText,
                        onValueChange = { newPageText = it },
                        label = { Text("Page name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val page = newPageText.trim()
                        if (page.isNotEmpty() && !pages.contains(page)) {
                            pages = pages + page
                            selectedPage = page
                            showPending = false
                            save()
                        }
                        newPageText = ""
                        showAddPage = false
                    }) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddPage = false }) { Text("Cancel") }
                }
            )
        }
    }
}

private fun loadTasks(raw: String): List<TaskItem> = try {
    val array = JSONArray(raw)
    (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        TaskItem(
            id = o.optLong("id", i.toLong()),
            title = o.optString("title"),
            page = o.optString("page", "My Tasks"),
            done = o.optBoolean("done"),
            pinned = o.optBoolean("pinned")
        )
    }
} catch (_: Exception) { emptyList() }

private fun tasksToJson(tasks: List<TaskItem>): String {
    val array = JSONArray()
    tasks.forEach { task ->
        array.put(JSONObject().apply {
            put("id", task.id)
            put("title", task.title)
            put("page", task.page)
            put("done", task.done)
            put("pinned", task.pinned)
        })
    }
    return array.toString()
}

private fun loadPages(raw: String): List<String> = try {
    val array = JSONArray(raw)
    (0 until array.length()).map { array.getString(it) }.ifEmpty { listOf("My Tasks") }
} catch (_: Exception) { listOf("My Tasks") }

private fun pagesToJson(pages: List<String>): String {
    val array = JSONArray()
    pages.forEach { array.put(it) }
    return array.toString()
}
