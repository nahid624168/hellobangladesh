package com.exambase.ai.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.exambase.ai.R
import com.exambase.ai.academic.CategoryViewModel
import com.exambase.ai.academic.LoadState
import com.exambase.ai.academic.SubcategoryViewModel
import com.exambase.ai.academic.SubjectDetailViewModel
import com.exambase.ai.academic.localized
import com.exambase.ai.auth.appViewModel
import com.exambase.core.designsystem.components.EmptyView
import com.exambase.core.designsystem.theme.Spacing

private val listPadding = PaddingValues(Spacing.md)
private val listSpacing = Arrangement.spacedBy(Spacing.sm)

@Composable
fun SubjectDetailScreen(id: String, onBack: () -> Unit, onOpenCategory: (String) -> Unit, onSignedOut: () -> Unit) {
    val vm = appViewModel { SubjectDetailViewModel(id, it.academicRepository) }
    val state by vm.state.collectAsState()
    val title = (state as? LoadState.Ready)?.data?.subject?.let { localized(it.title, it.titleBn) } ?: ""
    ContentScaffold(title, onBack) {
        LoadStateHost(state, vm::load, onSignedOut) { detail ->
            if (detail.categories.isEmpty()) {
                EmptyView(stringResource(R.string.empty_content_title), stringResource(R.string.empty_content_body))
            } else {
                LazyColumn(contentPadding = listPadding, verticalArrangement = listSpacing) {
                    item {
                        val description = localized(detail.subject.description, detail.subject.descriptionBn)
                        if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium)
                    }
                    item { Text(stringResource(R.string.categories_title), style = MaterialTheme.typography.titleMedium) }
                    items(detail.categories, key = { it.id }) { category ->
                        NodeCard(
                            title = localized(category.title, category.titleBn),
                            subtitle = localized(category.description, category.descriptionBn),
                            meta = stringResource(R.string.meta_category, category.subcategoryCount),
                            onClick = { onOpenCategory(category.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryScreen(id: String, onBack: () -> Unit, onOpenSubcategory: (String) -> Unit, onSignedOut: () -> Unit) {
    val vm = appViewModel { CategoryViewModel(id, it.academicRepository) }
    val state by vm.state.collectAsState()
    val title = (state as? LoadState.Ready)?.data?.category?.let { localized(it.title, it.titleBn) } ?: ""
    ContentScaffold(title, onBack) {
        LoadStateHost(state, vm::load, onSignedOut) { detail ->
            if (detail.subcategories.isEmpty()) {
                EmptyView(stringResource(R.string.empty_content_title), stringResource(R.string.empty_content_body))
            } else {
                LazyColumn(contentPadding = listPadding, verticalArrangement = listSpacing) {
                    item { Breadcrumbs(detail.breadcrumb) }
                    item {
                        val description = localized(detail.category.description, detail.category.descriptionBn)
                        if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium)
                    }
                    item { Text(stringResource(R.string.subcategories_title), style = MaterialTheme.typography.titleMedium) }
                    items(detail.subcategories, key = { it.id }) { sub ->
                        NodeCard(
                            title = localized(sub.title, sub.titleBn),
                            subtitle = localized(sub.description, sub.descriptionBn),
                            meta = stringResource(R.string.meta_subcategory, sub.lessonCount),
                            onClick = { onOpenSubcategory(sub.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubcategoryScreen(id: String, onBack: () -> Unit, onOpenLesson: (String) -> Unit, onSignedOut: () -> Unit) {
    val vm = appViewModel { SubcategoryViewModel(id, it.academicRepository) }
    val state by vm.state.collectAsState()
    RefreshOnReturn(vm::refresh)   // progress and bookmark marks change while reading a lesson
    val title = (state as? LoadState.Ready)?.data?.subcategory?.let { localized(it.title, it.titleBn) } ?: ""
    ContentScaffold(title, onBack) {
        LoadStateHost(state, vm::load, onSignedOut) { detail ->
            if (detail.lessons.isEmpty()) {
                EmptyView(stringResource(R.string.empty_content_title), stringResource(R.string.empty_content_body))
            } else {
                LazyColumn(contentPadding = listPadding, verticalArrangement = listSpacing) {
                    item { Breadcrumbs(detail.breadcrumb) }
                    item { Text(stringResource(R.string.lessons_title), style = MaterialTheme.typography.titleMedium) }
                    items(detail.lessons, key = { it.id }) { lesson -> LessonCard(lesson) { onOpenLesson(lesson.id) } }
                }
            }
        }
    }
}
