package com.alvarotc.bito

import android.app.Application
import com.alvarotc.bito.data.backup.BackupSync
import com.alvarotc.bito.ui.notifications.NotificationChannels
import com.alvarotc.bito.ui.notifications.ReminderSync
import com.alvarotc.bito.ui.onboarding.OnboardingReconciler
import com.alvarotc.bito.ui.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-wide services (channels, widget refresh, alarm sync) hang off here.
 *
 * Robolectric shares this `object` across every test in the same JVM, and [defaultScope] used to
 * never get cancelled: a test that reached [start] (directly or via `BitoApp.onCreate`) leaked its
 * three collectors into whatever test ran next — [com.alvarotc.bito.data.backup.BackupSync]'s
 * WorkManager write and [com.alvarotc.bito.ui.widget.WidgetRefresher]'s tray refresh included. This
 * is the root cause ledgered against `TrayRefresherTest`'s full-suite-only flake since M8. [start]
 * is now idempotent ([started] guards a second call) and takes an injectable [scope] parameter so a
 * test can supply a scope it fully controls and cancels — production keeps using [defaultScope].
 *
 * The manifest declares `BitoApp` as the app's `Application`, so any Robolectric test that doesn't
 * override `@Config(application = ...)` boots a real `BitoApp.onCreate()` — and with it a real
 * [start] call on [defaultScope] — as a side effect of Robolectric's own environment setup, whether
 * or not that test ever mentions [AppStartup] by name. [started] being a one-shot latch across the
 * whole JVM fork is what makes this safe: normally only the first such test in a fork ever launches
 * production collectors, not every one of them. [resetForTests] and [quiesceForTests] below exist
 * because [AppStartupTest] deliberately re-triggers [start] on its own injected scope and must not
 * re-arm that latch for whatever unrelated test happens to run next in the same fork — its
 * `@Before` does briefly reopen the latch for the span of its own methods (so ONE extra production
 * collector-set can launch on `defaultScope` if some other test boots `BitoApp` in that exact
 * window), but [quiesceForTests] closes it again for the rest of the fork's lifetime. That residual
 * is bounded (at most one per fork) and inert (it holds a reference to a torn-down test's `Context`
 * that nothing writes to again) — eliminating it entirely would mean auditing every Robolectric
 * class in the suite for `@Config(application = ...)`, out of scope here.
 */
object AppStartup {
    private val defaultScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = AtomicBoolean(false)

    /**
     * Test-only: how many times [start]'s body actually ran (as opposed to being no-op'd by
     * [started]) — incremented in lockstep with the four `.start()` calls inside it (three
     * continuous collectors plus [OnboardingReconciler]'s one-shot reconciler), so this is the hook
     * a test uses to prove a second [start] call doesn't launch a second set of them.
     */
    internal val startInvocations = AtomicInteger(0)

    fun start(
        app: Application,
        container: AppContainer,
        scope: CoroutineScope = defaultScope,
    ) {
        if (!started.compareAndSet(false, true)) return
        startInvocations.incrementAndGet()
        NotificationChannels.ensure(app)
        WidgetRefresher.start(app, container, scope)
        ReminderSync.start(app, container, scope)
        BackupSync.start(app, container, scope)
        OnboardingReconciler.start(container, scope)
    }

    /**
     * Test-only: gives a test a clean slate before it calls [start] itself, regardless of whether
     * some earlier, unrelated Robolectric test in the same JVM fork already tripped [started] via
     * an implicit `BitoApp.onCreate()` (see class KDoc). Clears [started] and [startInvocations],
     * and — as a safety net for a stray call that used [defaultScope] instead of an injected one —
     * cancels that shared scope's children without cancelling the scope itself, so a later
     * production [start] can still use it. Call from `@Before`, not `@After`: see
     * [quiesceForTests] for why the two are not interchangeable.
     */
    internal fun resetForTests() {
        started.set(false)
        startInvocations.set(0)
        defaultScope.coroutineContext[Job]?.cancelChildren()
    }

    /**
     * Test-only: cleans up after a test that called [start], WITHOUT clearing [started]. Calling
     * [resetForTests] here instead would re-arm the one-shot latch, and the very next unrelated
     * Robolectric test to boot `BitoApp` in this fork would then launch a fresh, uncancelled set of
     * production collectors on [defaultScope] — reintroducing exactly the leak this whole seam
     * exists to prevent. Call from `@After`.
     */
    internal fun quiesceForTests() {
        defaultScope.coroutineContext[Job]?.cancelChildren()
        started.set(true)
    }
}
