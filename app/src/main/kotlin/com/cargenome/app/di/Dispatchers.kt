package com.cargenome.app.di

import javax.inject.Qualifier

/** CPU-bound work: VIN decoding, consumption and schedule arithmetic. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Blocking I/O the framework does not already move off the main thread. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
