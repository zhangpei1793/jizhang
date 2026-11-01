package com.example.ledger.ui.category

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ledger.LedgerApplication
import com.example.ledger.R
import com.example.ledger.data.model.Category
import com.example.ledger.data.model.CategoryType
import com.example.ledger.data.model.TxType
import com.example.ledger.ui.components.CategoryIcons
import com.example.ledger.ui.components.EmptyState
import com.example.ledger.ui.components.LoadingBox
import com.example.ledger.ui.theme.AppBackground
import com.example.ledger.ui.theme.ExpenseRed
import com.example.ledger.ui.theme.IncomeGreen
import com.example.ledger.ui.theme.LedgerGreen
import com.example.ledger.ui.theme.LedgerGreenContainer
import com.example.ledger.ui.theme.TextSecondary
import com.example.ledger.util.resolve

/** 分类管理页 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as LedgerApplication
    val viewModel: CategoryViewModel = viewModel { CategoryViewModel(app.container) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    var editor by remember { mutableStateOf<EditorState?>(null) }
    var deleteTarget by remember { mutableStateOf<Category?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message.resolve(context), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.category_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editor = EditorState(
                        id = null,
                        name = "",
                        iconKey = "other",
                        type = viewModel.defaultTypeForTab()
                    )
                },
                containerColor = LedgerGreen
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.category_add),
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = state.tab,
                containerColor = AppBackground,
                contentColor = LedgerGreen
            ) {
                Tab(
                    selected = state.tab == TxType.EXPENSE,
                    onClick = { viewModel.selectTab(TxType.EXPENSE) },
                    text = { Text(stringResource(R.string.category_expense)) }
                )
                Tab(
                    selected = state.tab == TxType.INCOME,
                    onClick = { viewModel.selectTab(TxType.INCOME) },
                    text = { Text(stringResource(R.string.category_income)) }
                )
            }

            when {
                state.loading && state.categories.isEmpty() -> LoadingBox()
                state.visibleCategories.isEmpty() -> EmptyState(
                    text = stringResource(R.string.category_empty)
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items = state.visibleCategories, key = { it.id }) { category ->
                        CategoryRow(
                            category = category,
                            onEdit = {
                                editor = EditorState(
                                    id = category.id,
                                    name = category.name,
                                    iconKey = category.iconKey,
                                    type = category.type
                                )
                            },
                            onDelete = { deleteTarget = category }
                        )
                    }
                }
            }
        }
    }

    editor?.let { current ->
        CategoryEditorDialog(
            editor = current,
            onDismiss = { editor = null },
            onSave = { name, iconKey, type ->
                viewModel.save(current.id, name, iconKey, type)
                editor = null
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.category_delete_title)) },
            text = { Text(stringResource(R.string.category_delete_message, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target)
                        deleteTarget = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = ExpenseRed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** 单行分类：图标、名称、适用类型与操作按钮 */
@Composable
private fun CategoryRow(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = if (category.type == CategoryType.INCOME_ONLY) IncomeGreen else ExpenseRed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onEdit)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(LedgerGreenContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CategoryIcons.iconFor(category.iconKey),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (category.isDefault) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(LedgerGreenContainer)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.category_builtin),
                            style = MaterialTheme.typography.labelSmall,
                            color = LedgerGreen
                        )
                    }
                }
            }
            Text(
                text = categoryTypeLabel(category.type),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.action_edit),
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // 内置分类不可删除（数据库触发器同样会拦截）
        if (!category.isDefault) {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun categoryTypeLabel(type: Int): String = when (type) {
    CategoryType.EXPENSE_ONLY -> stringResource(R.string.category_type_expense)
    CategoryType.INCOME_ONLY -> stringResource(R.string.category_type_income)
    else -> stringResource(R.string.category_type_both)
}

/** 分类编辑弹窗：名称、图标、适用类型 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(
    editor: EditorState,
    onDismiss: () -> Unit,
    onSave: (name: String, iconKey: String, type: Int) -> Unit
) {
    val context = LocalContext.current
    var name by remember(editor) { mutableStateOf(editor.name) }
    var iconKey by remember(editor) { mutableStateOf(editor.iconKey) }
    var type by remember(editor) { mutableStateOf(editor.type) }
    var error by remember(editor) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (editor.id == null) R.string.category_add else R.string.category_edit
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.category_name)) },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { message ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.category_icon),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryIcons.options.forEach { key ->
                        IconOption(
                            iconKey = key,
                            selected = key == iconKey,
                            onClick = { iconKey = key }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.category_type),
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        CategoryType.EXPENSE_ONLY,
                        CategoryType.INCOME_ONLY,
                        CategoryType.BOTH
                    ).forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(categoryTypeLabel(option)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = name.trim()
                    when {
                        trimmed.isEmpty() -> error = context.getString(R.string.category_name_required)
                        trimmed.length > 20 -> error = context.getString(R.string.category_name_too_long)
                        else -> onSave(trimmed, iconKey, type)
                    }
                }
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun IconOption(
    iconKey: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) LedgerGreenContainer else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = CategoryIcons.iconFor(iconKey),
            contentDescription = null,
            tint = if (selected) LedgerGreen else TextSecondary,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** 编辑弹窗的初始数据 */
private data class EditorState(
    val id: String?,
    val name: String,
    val iconKey: String,
    val type: Int
)
