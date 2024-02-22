package com.example.image_multi_recognition.util

import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

// From https://medium.com/androiddevelopers/coroutines-on-android-part-iii-real-work-2ba8a2ec2f45
//      https://gist.github.com/objcode/7ab4e7b1df8acd88696cb0ccecad16f7
// The code here does not guarantee only one of the multiple tasks runs,
// But it guarantees that if more than one tasks run, they run sequentially
//      (the next one starts only when the previous one is finished,
//      instead of multiple same tasks (triggered by pressing button too fast) runs in multiple threads, which may cause bugs due to concurrency problem.
//      It guarantees that there is only one task running at a time to get rid of the concurrency problem)
// And it does its best to cancel/join the previous task to reduce the duplicate requests in a short time'

// We’ll explore ways to use coroutines to leave the button enabled but ensure that
// one shot requests are executed in an order that does not surprise the user (convert from concurrency to sequential).
// We can do that by avoiding accidental concurrency by controlling when the coroutines run (or do not run).
class SingleMutexRunner {
    private val mutex = Mutex()

    suspend fun <T> afterPrevious(block: suspend () -> T): T {
        return mutex.withLock {
            block()
        }
    }
}

class ControlledRunner<T> {
    private val activeTask = AtomicReference<Deferred<T>?>(null)

    // This method may throw "CancellationException", you should handle it properly to use it
    suspend fun cancelPreviousThenRun(block: suspend () -> T): T {
        // cancel and make sure the previous task is canceled
        // if the previous "activeTask" is waiting in a suspend function, then it finishes immediately by a CancellationException
        // otherwise if the previous "activeTask" is doing some computation,
        // the cancel() does not work unless the other coroutine checks while(isActive) constantly
        // So you need cancelAndJoin() instead of just cancel() to make sure the previous task is cancelled, other than just signal cancellation
        activeTask.get()?.cancelAndJoin()

        // by using coroutineScope(), you create a new coroutine scope that attached to its parent scope
        // Note the code inside lambda "...": coroutineScope {...} throws exception to the function call coroutineScope() directly
        // It means that if the following "result = newTask.await()" throws a Cancellation exception,
        // the exception is thrown to cancelPreviousThenRun() function call,
        // which means calling "cancelPreviousThenRun()" may get an unexpected CancellationException

        // Note that if CancellationException is thrown inside the lambda of launch{...} or async{...}
        // by calling deferred.await(), then it is ignored if you do not catch it.
        // However, if it is thrown inside the lambda of Coroutine Builder like "coroutineScope{...}"
        // The exception is regarded as the exception when calling a function like "coroutineScope"
        // And you do not use try-catch in cancelPreviousThenRun, the exception is thrown by cancelPreviousThenRun()
        return coroutineScope {
            val newTask = async(start = CoroutineStart.LAZY) {
                block()
            }
            newTask.invokeOnCompletion {
                activeTask.compareAndSet(newTask, null)
            }

            val result: T
            // for multi-thread, the "activeTask.get()?.cancelAndJoin()" does not guarantee
            // that only one task reaches here.
            // A while(true) is needed to make sure only one task survives the competition
            // regardless of how those tasks are triggered
            while (true) {
                if (!activeTask.compareAndSet(null, newTask)) {
                    // cancel the previous task, causes CancellationException thrown to function coroutineScope()
                    // in the coroutine of the previous task
                    activeTask.get()?.cancelAndJoin()
                    yield() // suspend current coroutine and let other coroutines run
                } else {
                    // await() has the same effect as yield that suspends the current coroutine and let other coroutines runs
                    result = newTask.await()
                    break
                }
            }
            result
        }
    }

    // This method does not throw "CancellationException" in normal case
    // Handle a situation where a user may click a button multiple times, which generates duplicated requests
    // If the dispatcher has only one thread (like Dispatcher.Main), then the task runs only once.
    // If the dispatcher has multiple threads, the code below is thread-safe. However,
    // it does not guarantee that if you start A, B, C three tasks only one of them runs completely,
    // it just does its best to join the previous spotted coroutine and return the result
    suspend fun joinPreviousOrRun(block: suspend () -> T): T {
        activeTask.get()?.let {
            return it.await()
        }
        return coroutineScope {
            val newTask = async(start = CoroutineStart.LAZY) {
                block()
            }
            newTask.invokeOnCompletion {
                activeTask.compareAndSet(newTask, null)
            }

            val result: T
            while (true) {
                if (!activeTask.compareAndSet(null, newTask)) {
                    // although activeTask is not null previously, it may be null be due to concurrency
                    val currentTask = activeTask.get()
                    if (currentTask != null) {
                        newTask.cancel()
                        result = currentTask.await()
                        break
                    } else {
                        // Retry path - "activeTask" completed before we could get it,
                        // loop to try setting activeTask again to make sure calling joinPreviousOrRun() always return a meaningful result
                        // call yield here in case we're executing on a single threaded dispatcher
                        // like Dispatchers.Main to allow other work to happen.

                        // If you reach here, you may assume that the same tasks runs at least twice.
                        // But the second task runs after the first task finished (sequentially),
                        // not two tasks run concurrently in multiple thread
                        yield()
                    }
                } else {
                    result = newTask.await()
                    break
                }
            }
            result
        }
    }
}

// You may run the following code for testing
//fun main(): Unit {
//    val counter = AtomicInteger(1)
//    val runner = ControlledRunner<Int>()
//    runBlocking {
//        withContext(Dispatchers.IO) {
//            repeat(10) {
//                launch {
//                    try {
//                        // val res = runner.cancelPreviousThenRun {
//                        // The winner does not necessary to be the coroutine launched last in multiple threads like Dispatchers.IO
//                        // The result, "res", may not be 1 for Dispatchers.IO while for single thread it is 1
//                        val res = runner.cancelPreviousThenRun {
//                            val waitTime = counter.getAndIncrement()
//                            delay(1000)
//                            waitTime
//                        }
//                        println(res)
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                    }
//                }
//            }
//        }
//    }
//    println("After running, counter=$counter")
//}