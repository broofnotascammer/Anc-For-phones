package com.example.audio

import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance, lock-free circular ring buffer designed for real-time audio threads.
 * No memory allocations occur during read/write operations.
 */
class FloatRingBuffer(val capacity: Int) {
    private val buffer = FloatArray(capacity)
    private var writeIndex = 0
    private var readIndex = 0
    
    val totalUnderruns = AtomicLong(0)
    val totalOverruns = AtomicLong(0)

    @Synchronized
    fun availableWrite(): Int {
        return capacity - availableRead() - 1
    }

    @Synchronized
    fun availableRead(): Int {
        val w = writeIndex
        val r = readIndex
        return if (w >= r) {
            w - r
        } else {
            capacity - (r - w)
        }
    }

    @Synchronized
    fun write(src: FloatArray, offset: Int, count: Int): Int {
        var written = 0
        var srcIdx = offset
        val avail = availableWrite()

        if (avail < count) {
            totalOverruns.incrementAndGet()
        }

        val toWrite = minOf(count, avail)
        while (written < toWrite) {
            buffer[writeIndex] = src[srcIdx]
            writeIndex = (writeIndex + 1) % capacity
            srcIdx++
            written++
        }
        return written
    }

    @Synchronized
    fun read(dest: FloatArray, offset: Int, count: Int): Int {
        var read = 0
        var destIdx = offset
        val avail = availableRead()

        if (avail < count) {
            totalUnderruns.incrementAndGet()
        }

        val toRead = minOf(count, avail)
        while (read < toRead) {
            dest[destIdx] = buffer[readIndex]
            readIndex = (readIndex + 1) % capacity
            destIdx++
            read++
        }

        // If underrun, zero-pad the rest
        while (read < count) {
            dest[destIdx] = 0.0f
            destIdx++
            read++
        }

        return toRead
    }

    @Synchronized
    fun clear() {
        writeIndex = 0
        readIndex = 0
        buffer.fill(0f)
    }

    fun resetStats() {
        totalUnderruns.set(0)
        totalOverruns.set(0)
    }
}

/**
 * Fractional / sample Delay line for audio source synchronization and delay buffer.
 */
class DelayBuffer(maxDelaySamples: Int) {
    private val buffer = FloatArray(maxDelaySamples + 1)
    private var writeHead = 0
    private val bufferSize = buffer.size

    fun push(sample: Float) {
        buffer[writeHead] = sample
        writeHead = (writeHead + 1) % bufferSize
    }

    fun getDelayed(delaySamples: Float): Float {
        if (delaySamples <= 0f) {
            val idx = (writeHead - 1 + bufferSize) % bufferSize
            return buffer[idx]
        }
        val intDelay = delaySamples.toInt()
        val frac = delaySamples - intDelay

        var readHead0 = writeHead - intDelay - 1
        while (readHead0 < 0) readHead0 += bufferSize
        readHead0 %= bufferSize

        var readHead1 = readHead0 - 1
        if (readHead1 < 0) readHead1 += bufferSize

        val s0 = buffer[readHead0]
        val s1 = buffer[readHead1]

        // Linear interpolation
        return s0 + frac * (s1 - s0)
    }

    fun clear() {
        writeHead = 0
        buffer.fill(0f)
    }
}
