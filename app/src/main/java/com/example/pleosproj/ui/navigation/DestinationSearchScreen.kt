package com.example.pleosproj.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pleosproj.ui.guidance.distanceText
import com.example.pleosproj.ui.permissions.*

private val Ink = androidx.compose.ui.graphics.Color(0xFF232228)
private val Muted = androidx.compose.ui.graphics.Color(0xFF68666D)

@Composable
internal fun DestinationSearchScreen(flow: NavigationFlowController, permissions: List<AccessStatus>,
    onPermission: (AppAccess) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val canInput = flow.parked == true
    fun submit() { focus.clearFocus(); keyboard?.hide(); flow.search() }
    LaunchedEffect(canInput) { if (!canInput) { focus.clearFocus(); keyboard?.hide() } }
    Column(modifier.fillMaxSize().imePadding().padding(28.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("어디로 안내할까요?", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text("강남역 출발 · 삼성역까지의 예시 경로를 확인해 보세요", fontSize = 24.sp, color = Muted)
            }
            var showExamples by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { showExamples = true }, enabled = canInput) { Text("검색 상태 예시", fontSize = 21.sp) }
                DropdownMenu(expanded = showExamples, onDismissRequest = { showExamples = false }) {
                    SearchExample.entries.forEach { example ->
                        DropdownMenuItem(text = { Text(example.title, fontSize = 23.sp) }, onClick = {
                            flow.chooseExample(example); showExamples = false
                        })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = flow.query, onValueChange = flow::editQuery, enabled = canInput,
                modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(18.dp),
                textStyle = TextStyle(fontSize = 29.sp, color = Ink),
                placeholder = { Text("목적지 이름이나 주소 검색", fontSize = 29.sp) },
                trailingIcon = {
                    if (flow.query.isNotEmpty()) TextButton(onClick = { flow.editQuery("") }, enabled = canInput) {
                        Text("지우기", fontSize = 21.sp)
                    }
                }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }))
            Button(onClick = { submit() }, enabled = canInput && flow.query.isNotBlank() && flow.searchContent != SearchContent.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = Ink), shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp)) {
                Text("검색", fontSize = 29.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (!canInput) Text(if (flow.parked == false) "주행 중에는 목적지를 검색할 수 없어요. 정차 후 이용해 주세요."
            else "정차 상태를 확인하고 있어요. 확인 후 목적지를 검색할 수 있습니다.", fontSize = 25.sp, color = Muted)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val content = flow.searchContent) {
                SearchContent.Recent -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("최근 목적지", fontSize = 29.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        TextButton(onClick = { flow.editQuery("삼성역"); submit() }, enabled = canInput) {
                            Text("삼성역 검색해 보기", fontSize = 23.sp)
                        }
                    }
                    PlaceList(flow.recent, canInput, focus, flow::selectPlace)
                }
                SearchContent.Loading -> SearchMessage("목적지를 찾고 있어요", "잠시만 기다려 주세요", loading = true)
                is SearchContent.Results -> if (content.places.isEmpty()) {
                    SearchMessage("검색 결과가 없어요", "다른 이름이나 주소로 검색해 주세요. 예시 목적지는 삼성역입니다.",
                        onRetry = { submit() }, enabled = canInput)
                } else Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("검색 결과 ${content.places.size}개", fontSize = 29.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    PlaceList(content.places, canInput, focus, flow::selectPlace)
                }
                is SearchContent.Failed -> SearchMessage(
                    when (content.reason) {
                        SearchExample.KEY_MISSING -> "아직 검색을 이용할 수 없어요"
                        SearchExample.LIMIT -> "잠시 후 다시 검색해 주세요"
                        else -> "검색 서비스에 연결하지 못했어요"
                    }, when (content.reason) {
                        SearchExample.KEY_MISSING -> "검색 서비스 설정을 준비하고 있습니다."
                        SearchExample.LIMIT -> "검색 요청이 많아요. 잠시 기다린 뒤 다시 시도해 주세요."
                        else -> "인터넷 연결을 확인한 뒤 다시 시도해 주세요."
                    }, onRetry = { submit() }, enabled = canInput)
            }
        }
        if (flow.example != SearchExample.RESULTS) Text("검색 상태 예시 · ${flow.example.title}", fontSize = 20.sp, color = Muted)
        PermissionCard(permissions, onPermission)
    }
}

@Composable
private fun PlaceList(places: List<PlaceExample>, enabled: Boolean, focus: FocusManager, onSelect: (PlaceExample) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        items(places, key = { it.id }) { place ->
            Surface(onClick = { focus.clearFocus(); onSelect(place) }, enabled = enabled,
                color = androidx.compose.ui.graphics.Color.White, shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(26.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(place.name, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                        Text(place.address, fontSize = 24.sp, color = Muted)
                    }
                    Text(distanceText(place.distanceMeters), fontSize = 26.sp, color = Muted)
                    Text("경로 보기 →", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(title: String, detail: String, loading: Boolean = false, enabled: Boolean = true, onRetry: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (loading) CircularProgressIndicator()
            Text(title, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(detail, fontSize = 24.sp, color = Muted)
            if (onRetry != null) OutlinedButton(onClick = onRetry, enabled = enabled,
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 18.dp)) { Text("다시 시도", fontSize = 25.sp) }
        }
    }
}
