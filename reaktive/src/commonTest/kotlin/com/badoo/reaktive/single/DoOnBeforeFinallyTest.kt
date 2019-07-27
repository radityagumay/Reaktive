package com.badoo.reaktive.single

import com.badoo.reaktive.base.exceptions.CompositeException
import com.badoo.reaktive.disposable.disposable
import com.badoo.reaktive.test.base.assertDisposed
import com.badoo.reaktive.test.base.assertError
import com.badoo.reaktive.test.single.DefaultSingleObserver
import com.badoo.reaktive.test.single.TestSingle
import com.badoo.reaktive.test.single.test
import com.badoo.reaktive.utils.atomic.AtomicInt
import com.badoo.reaktive.utils.atomic.atomicList
import com.badoo.reaktive.utils.atomic.plusAssign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DoOnBeforeFinallyTest
    : SingleToSingleTests by SingleToSingleTests<Unit>({ doOnBeforeFinally {} }) {

    private val upstream = TestSingle<Int>()

    @Test
    fun calls_action_before_success() {
        val callOrder = atomicList<String>()

        upstream
            .doOnBeforeFinally {
                callOrder += "action"
            }
            .subscribe(
                object : DefaultSingleObserver<Int> {
                    override fun onSuccess(value: Int) {
                        callOrder += "onSuccess"
                    }
                }
            )

        upstream.onSuccess(0)

        assertEquals(listOf("action", "onSuccess"), callOrder.value)
    }

    @Test
    fun calls_action_before_failing() {
        val callOrder = atomicList<String>()
        val exception = Exception()

        upstream
            .doOnBeforeFinally {
                callOrder += "action"
            }
            .subscribe(
                object : DefaultSingleObserver<Int> {
                    override fun onError(error: Throwable) {
                        callOrder += "onError"
                    }
                }
            )

        upstream.onError(exception)

        assertEquals(listOf("action", "onError"), callOrder.value)
    }

    @Test
    fun calls_action_before_disposing_upstream() {
        val callOrder = atomicList<String>()

        singleUnsafe<Unit> { observer ->
            observer.onSubscribe(
                disposable {
                    callOrder += "dispose"
                }
            )
        }
            .doOnBeforeFinally {
                callOrder += "action"
            }
            .test()
            .dispose()

        assertEquals(listOf("action", "dispose"), callOrder.value)
    }

    @Test
    fun does_not_call_action_second_time_WHEN_downstream_disposed_and_upstream_succeded() {
        val count = AtomicInt()

        upstream
            .doOnBeforeFinally {
                count.addAndGet(1)
            }
            .test()
            .dispose()

        upstream.onSuccess(0)

        assertEquals(1, count.value)
    }

    @Test
    fun does_not_call_action_second_time_WHEN_downstream_disposed_and_upstream_produced_error() {
        val count = AtomicInt()

        upstream
            .doOnBeforeFinally {
                count.addAndGet(1)
            }
            .test()
            .dispose()

        upstream.onError(Throwable())

        assertEquals(1, count.value)
    }

    @Test
    fun does_not_call_action_second_time_WHEN_upstream_succeeded_and_downstream_disposed() {
        val count = AtomicInt()

        val observer =
            upstream
                .doOnBeforeFinally {
                    count.addAndGet(1)
                }
                .test()

        upstream.onSuccess(0)
        observer.dispose()

        assertEquals(1, count.value)
    }

    @Test
    fun does_not_call_action_second_time_WHEN_upstream_produced_error_and_downstream_disposed() {
        val count = AtomicInt()

        val observer =
            upstream
                .doOnBeforeFinally {
                    count.addAndGet(1)
                }
                .test()

        upstream.onError(Throwable())
        observer.dispose()

        assertEquals(1, count.value)
    }

    @Test
    fun produces_error_WHEN_upstream_succeeded_and_exception_in_lambda() {
        val error = Exception()

        val observer =
            upstream
                .doOnBeforeFinally { throw error }
                .test()

        upstream.onSuccess(0)

        observer.assertError(error)
    }

    @Test
    fun produces_error_WHEN_downstream_disposed_and_exception_in_lambda() {
        val error = Exception()

        val observer =
            upstream
                .doOnBeforeFinally { throw error }
                .test()

        observer.dispose()

        observer.assertError(error)
    }

    @Test
    fun disposes_upstream_WHEN_downstream_disposed_and_exception_in_lambda() {
        val error = Exception()

        val observer =
            upstream
                .doOnBeforeFinally { throw error }
                .test()

        observer.dispose()

        observer.assertDisposed()
    }

    @Test
    fun produces_CompositeException_WHEN_upstream_produced_error_and_exception_in_lambda() {
        val error1 = Exception()
        val error2 = Exception()

        val observer =
            upstream
                .doOnBeforeFinally { throw error2 }
                .test()

        upstream.onError(error1)

        val error: Throwable? = observer.error
        assertTrue(error is CompositeException)
        assertSame(error1, error.cause1)
        assertSame(error2, error.cause2)
    }
}
