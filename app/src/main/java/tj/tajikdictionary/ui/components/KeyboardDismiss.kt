package tj.tajikdictionary.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Inserts [ch] at the caret (replacing any selection) and places the caret right after it. */
fun TextFieldValue.insertAtCaret(ch: Char): TextFieldValue {
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(0, text.length)
    return TextFieldValue(text.replaceRange(start, end, ch.toString()), TextRange(start + 1))
}

/**
 * Tracks screen regions (text fields, on-screen Tajik letter rows) where a tap must NOT close
 * the soft keyboard. Everything else inside [KeyboardDismissRoot] hides it on tap or scroll.
 */
class KeyboardDismissRegistry {
    internal val exempt = HashMap<Any, Rect>()
    fun isExempt(pointInRoot: Offset): Boolean = exempt.values.any { it.contains(pointInRoot) }
}

val LocalKeyboardDismissRegistry = staticCompositionLocalOf<KeyboardDismissRegistry?> { null }

/** Marks this element (and its children) as a region whose taps keep the keyboard open. */
fun Modifier.keyboardDismissExempt(): Modifier = composed {
    val registry = LocalKeyboardDismissRegistry.current ?: return@composed this
    val key = remember { Any() }
    DisposableEffect(registry, key) { onDispose { registry.exempt.remove(key) } }
    this.onGloballyPositioned { registry.exempt[key] = it.boundsInRoot() }
}

/**
 * Wraps the app so any tap or drag that starts outside an exempt region clears focus and hides the
 * keyboard. Events are observed in the Initial pass and never consumed, so buttons, list rows and
 * scrolling keep working normally.
 */
@Composable
fun KeyboardDismissRoot(modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    val registry = remember { KeyboardDismissRegistry() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val rootCoords = remember { arrayOfNulls<LayoutCoordinates>(1) }

    val dismissModifier = modifier
        .onGloballyPositioned { rootCoords[0] = it }
        .pointerInput(registry, focusManager, keyboard, touchSlop) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val origin = rootCoords[0]?.takeIf { it.isAttached }?.positionInRoot() ?: Offset.Zero
                if (registry.isExempt(down.position + origin)) return@awaitEachGesture
                var dismissed = false
                fun dismiss() {
                    if (dismissed) return
                    dismissed = true
                    focusManager.clearFocus()
                    keyboard?.hide()
                }
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        dismiss() // tap released
                        break
                    }
                    if ((change.position - down.position).getDistance() > touchSlop) {
                        dismiss() // scroll / drag started
                        break
                    }
                }
            }
        }

    CompositionLocalProvider(LocalKeyboardDismissRegistry provides registry) {
        content(dismissModifier)
    }
}
