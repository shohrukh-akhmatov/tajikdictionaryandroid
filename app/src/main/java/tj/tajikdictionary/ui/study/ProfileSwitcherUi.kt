package tj.tajikdictionary.ui.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tj.tajikdictionary.R
import tj.tajikdictionary.domain.model.GamificationSummary
import tj.tajikdictionary.domain.model.LearnerProfile
import tj.tajikdictionary.domain.model.ProfileKind
import tj.tajikdictionary.domain.model.ProfileStatsSnapshot
import tj.tajikdictionary.domain.model.WordGameEngine
import tj.tajikdictionary.ui.components.AppCard
import tj.tajikdictionary.ui.components.keyboardDismissExempt

/** Parses a `#RRGGBB` hex string into a Compose [Color], with a safe fallback. */
internal fun parseProfileColor(hex: String): Color {
    val clean = hex.trim().removePrefix("#")
    val rgb = clean.toLongOrNull(16) ?: return Color(0xFF087653)
    return Color(0xFF000000 or (rgb and 0xFFFFFF))
}

@Composable
internal fun LearnerProfile.localizedDisplayName(): String =
    if (id == LearnerProfile.DEFAULT_PROFILE_ID && name == LearnerProfile.DEFAULT_NAME) {
        stringResource(R.string.profile_default_name)
    } else {
        name
    }

@Composable
internal fun ProfileAvatarCircle(
    avatar: String,
    colorHex: String,
    size: Dp = 36.dp,
    fontSize: TextUnit = 18.sp,
    modifier: Modifier = Modifier,
) {
    val bg = parseProfileColor(colorHex)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bg.copy(alpha = 0.18f))
            .border(1.5.dp, bg.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = avatar, fontSize = fontSize)
    }
}

/** Top-bar chip on the Learn screen showing the active learner and opening the switcher in 1 tap. */
@Composable
fun ProfileHeaderChip(
    activeProfile: LearnerProfile,
    profileCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = parseProfileColor(activeProfile.colorHex)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = modifier.padding(end = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ProfileAvatarCircle(
                avatar = activeProfile.avatar,
                colorHex = activeProfile.colorHex,
                size = 28.dp,
                fontSize = 15.sp,
            )
            Text(
                text = activeProfile.localizedDisplayName(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (activeProfile.isChild) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accent.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = stringResource(R.string.profile_kind_child),
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
            } else if (profileCount > 1) {
                Text(
                    text = "$profileCount/${LearnerProfile.MAX_PROFILES}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.profile_switch),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Multi-user card on the Learn page:
 * - When 2..3 profiles exist: shows the Family & Learners board with streaks, XP, today's star, and 1-tap switching.
 * - When 1 profile exists: shows an invite card to add a family member (up to 3 local profiles).
 */
@Composable
fun FamilyLearnersCard(
    profiles: List<LearnerProfile>,
    activeProfile: LearnerProfile,
    activeSummary: GamificationSummary,
    statsByProfile: Map<String, ProfileStatsSnapshot>,
    onSelectProfile: (String) -> Unit,
    onOpenSwitcher: () -> Unit,
    onAddProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val todayKey = remember { WordGameEngine.dateKey(System.currentTimeMillis()) }

    if (profiles.size <= 1) {
        AppCard(modifier = modifier) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.family_invite_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = "1/${LearnerProfile.MAX_PROFILES}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.family_invite_sub, LearnerProfile.MAX_PROFILES),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = onAddProfile,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.profile_add_learner))
                }
            }
        }
        return
    }

    // Build live stats for active profile + cached stats for other profiles, ranked by XP then streak.
    val rows = remember(profiles, activeProfile, activeSummary, statsByProfile, todayKey) {
        profiles.map { profile ->
            val snapshot = if (profile.id == activeProfile.id) {
                ProfileStatsSnapshot(
                    profileId = profile.id,
                    currentStreakDays = activeSummary.currentStreakDays,
                    longestStreakDays = activeSummary.longestStreakDays,
                    totalXp = activeSummary.totalXp,
                    masteredCount = activeSummary.masteredCount,
                    knownWordsCount = activeSummary.estimatedKnownWords,
                    todayReviewed = activeSummary.todayReviewed,
                    dailyGoal = activeSummary.dailyGoal,
                    cefrCode = activeSummary.cefrLevel.code,
                    lastActiveDateKey = if (activeSummary.todayReviewed > 0) todayKey else statsByProfile[profile.id]?.lastActiveDateKey,
                )
            } else {
                statsByProfile[profile.id] ?: ProfileStatsSnapshot(
                    profileId = profile.id,
                    dailyGoal = profile.kind.defaultDailyGoal,
                )
            }
            profile to snapshot
        }.sortedWith(
            compareByDescending<Pair<LearnerProfile, ProfileStatsSnapshot>> { it.second.totalXp }
                .thenByDescending { it.second.currentStreakDays },
        )
    }

    AppCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.family_board_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                stringResource(R.string.profile_limit_badge, profiles.size, LearnerProfile.MAX_PROFILES),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.family_board_subtitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onOpenSwitcher) {
                    Text(stringResource(R.string.profile_switch))
                }
            }

            rows.forEach { (profile, stat) ->
                val isCurrent = profile.id == activeProfile.id
                val accent = parseProfileColor(profile.colorHex)
                val practicedToday = stat.practicedToday(todayKey)
                Surface(
                    onClick = { if (!isCurrent) onSelectProfile(profile.id) else onOpenSwitcher() },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isCurrent) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = if (isCurrent) BorderStroke(1.5.dp, accent.copy(alpha = 0.5f)) else null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ProfileAvatarCircle(
                            avatar = profile.avatar,
                            colorHex = profile.colorHex,
                            size = 38.dp,
                            fontSize = 19.sp,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    profile.localizedDisplayName(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (isCurrent) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = accent,
                                    ) {
                                        Text(
                                            stringResource(R.string.family_active_badge),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                                if (profile.isChild) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = accent.copy(alpha = 0.18f),
                                    ) {
                                        Text(
                                            stringResource(R.string.profile_kind_child),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accent,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                stringResource(R.string.family_stat_summary, stat.totalXp, stat.knownWordsCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (practicedToday) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = stringResource(R.string.family_practiced_today),
                                tint = Color(0xFFFFB300),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint = if (stat.currentStreakDays > 0) Color(0xFFFF6D00) else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                "${stat.currentStreakDays}d",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (stat.currentStreakDays > 0) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (profiles.size < LearnerProfile.MAX_PROFILES) {
                OutlinedButton(
                    onClick = onAddProfile,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.profile_add_learner))
                }
            }
        }
    }
}

/** Bottom sheet listing all local learner profiles (up to 3), with 1-tap switching, add, and edit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSwitcherSheet(
    profiles: List<LearnerProfile>,
    activeProfile: LearnerProfile,
    statsByProfile: Map<String, ProfileStatsSnapshot>,
    onSelectProfile: (String) -> Unit,
    onAddProfileClick: () -> Unit,
    onEditProfileClick: (LearnerProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.profile_who_is_learning),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        stringResource(R.string.profile_limit_badge, profiles.size, LearnerProfile.MAX_PROFILES),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                stringResource(R.string.profile_who_is_learning_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            profiles.forEach { profile ->
                val isSelected = profile.id == activeProfile.id
                val accent = parseProfileColor(profile.colorHex)
                val stat = statsByProfile[profile.id]
                Surface(
                    onClick = { onSelectProfile(profile.id) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) accent else Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ProfileAvatarCircle(
                            avatar = profile.avatar,
                            colorHex = profile.colorHex,
                            size = 46.dp,
                            fontSize = 22.sp,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    profile.localizedDisplayName(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (profile.isChild) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = accent.copy(alpha = 0.2f),
                                    ) {
                                        Text(
                                            stringResource(R.string.profile_kind_child),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accent,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            val subText = if (stat != null) {
                                "\uD83D\uDD25 ${stat.currentStreakDays}d · ${stat.totalXp} XP · ${stat.cefrCode}"
                            } else {
                                stringResource(
                                    if (profile.isChild) R.string.profile_kind_child_sub else R.string.profile_kind_adult_sub,
                                )
                            }
                            Text(
                                subText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        IconButton(onClick = { onEditProfileClick(profile) }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.profile_edit_learner),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            if (profiles.size < LearnerProfile.MAX_PROFILES) {
                Button(
                    onClick = onAddProfileClick,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_add_learner))
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.profile_limit_reached, LearnerProfile.MAX_PROFILES),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Icon(
                    Icons.Filled.CloudDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(R.string.profile_cloud_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Dialog to create a new learner profile or edit/delete an existing profile. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditorDialog(
    initialProfile: LearnerProfile?,
    existingCount: Int,
    canDelete: Boolean,
    onSave: (name: String, avatar: String, colorHex: String, kind: ProfileKind) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val defaultName = if (initialProfile != null) initialProfile.localizedDisplayName() else ""
    var name by remember(initialProfile) { mutableStateOf(defaultName) }
    var avatar by remember(initialProfile) {
        mutableStateOf(
            initialProfile?.avatar
                ?: LearnerProfile.AVATARS.getOrElse(existingCount % LearnerProfile.AVATARS.size) { LearnerProfile.DEFAULT_AVATAR },
        )
    }
    var colorHex by remember(initialProfile) {
        mutableStateOf(
            initialProfile?.colorHex
                ?: LearnerProfile.COLORS.getOrElse(existingCount % LearnerProfile.COLORS.size) { LearnerProfile.DEFAULT_COLOR },
        )
    }
    var kind by remember(initialProfile) { mutableStateOf(initialProfile?.kind ?: ProfileKind.ADULT) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && initialProfile != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.profile_delete_confirm_title, initialProfile.localizedDisplayName())) },
            text = { Text(stringResource(R.string.profile_delete_confirm_msg, initialProfile.localizedDisplayName())) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(if (initialProfile == null) R.string.profile_add_learner else R.string.profile_edit_learner),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProfileAvatarCircle(
                        avatar = avatar,
                        colorHex = colorHex,
                        size = 54.dp,
                        fontSize = 26.sp,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 24) name = it },
                        label = { Text(stringResource(R.string.profile_name_label)) },
                        placeholder = { Text(stringResource(R.string.profile_name_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).keyboardDismissExempt(),
                    )
                }

                Text(
                    stringResource(R.string.profile_avatar_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LearnerProfile.AVATARS.forEach { item ->
                        val selected = item == avatar
                        val accent = parseProfileColor(colorHex)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) accent.copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                .border(
                                    width = if (selected) 2.dp else 0.dp,
                                    color = if (selected) accent else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable { avatar = item },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(item, fontSize = 20.sp)
                        }
                    }
                }

                Text(
                    stringResource(R.string.profile_color_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LearnerProfile.COLORS.forEach { hex ->
                        val c = parseProfileColor(hex)
                        val selected = hex.equals(colorHex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(c)
                                .clickable { colorHex = hex },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                Text(
                    stringResource(R.string.profile_kind_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(ProfileKind.ADULT, R.string.profile_kind_adult, R.string.profile_kind_adult_sub),
                        Triple(ProfileKind.CHILD, R.string.profile_kind_child, R.string.profile_kind_child_sub),
                    ).forEach { (option, titleRes, subRes) ->
                        val selected = kind == option
                        val accent = parseProfileColor(colorHex)
                        Surface(
                            onClick = { kind = option },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) accent else Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(titleRes),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        stringResource(subRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (selected) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = accent)
                                }
                            }
                        }
                    }
                }

                if (initialProfile != null && canDelete && onDelete != null) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.profile_delete))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = name.trim().ifEmpty { LearnerProfile.DEFAULT_NAME }
                    onSave(finalName, avatar, colorHex, kind)
                },
            ) {
                Text(stringResource(R.string.profile_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
