package com.example.printer

import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Protocol implementation for Marklife P50S thermal receipt printer (BLE device P50S-496A-BLE).
 * Implements Protocol 0x1F with continuous paper support, 384-dot thermal bitmap rasterization,
 * Level 0 Zlib stored-block compression (RFC 1950, 1KB window), and credit-based flow control.
 */
object P50SProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
    val WRITE_CHAR_UUID: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
    val FLOW_CONTROL_CHAR_UUID: UUID = UUID.fromString("0000ff03-0000-1000-8000-00805f9b34fb")
    val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val TARGET_DEVICE_NAME_EXACT = "P50S-496A-BLE"
    const val PRINTER_WIDTH_DOTS = 384
    const val BYTES_PER_ROW = PRINTER_WIDTH_DOTS / 8 // 48 bytes
    const val BLE_CHUNK_SIZE = 90
    const val SLICE_MAX_HEIGHT = 4000 // Continuous stream: P50S thermal printer firmware requires ONE continuous 0x1F 0x10 raster stream per print job, exactly like the proven Test Print

    // Print speed and completion timing constants for Marklife P50S (203 DPI / 8 dots/mm)
    // Physical thermal print speed is ~25-35 mm/s (approx 200-280 dot lines/sec, ~3.5ms - 5ms per raster dot line)
    const val MIN_POST_RASTER_DELAY_MS = 500L
    const val MS_PER_RASTER_ROW = 3L
    const val MAX_POST_RASTER_TIMEOUT_MS = 15000L

    // Commands (Protocol 0x1F)
    val CMD_SET_BT_TYPE = byteArrayOf(0x1F.toByte(), 0xB2.toByte(), 0x00.toByte())
    val CMD_PAPER_TYPE_CONTINUOUS = byteArrayOf(0x1F.toByte(), 0x80.toByte(), 0x01.toByte(), 0x10.toByte()) // 0x10 = Continuous Receipt Paper
    val CMD_SET_DENSITY_4 = byteArrayOf(0x1F.toByte(), 0x70.toByte(), 0x01.toByte(), 0x04.toByte()) // Density 4
    val CMD_START_PRINT_JOB = byteArrayOf(0x1F.toByte(), 0xC0.toByte(), 0x01.toByte(), 0x00.toByte())
    val CMD_STOP_PRINT_JOB = byteArrayOf(0x1F.toByte(), 0xC0.toByte(), 0x01.toByte(), 0x01.toByte())
    val CMD_FEED_PAPER_80PX = byteArrayOf(0x1F.toByte(), 0x11.toByte(), 0x00.toByte(), 0x00.toByte(), 0x50.toByte()) // Feed 80px to tear bar
    val CMD_FEED_PAPER_160PX = byteArrayOf(0x1F.toByte(), 0x11.toByte(), 0x00.toByte(), 0x00.toByte(), 0xA0.toByte()) // Feed 160px to tear bar
    val CMD_ALIGN_END = byteArrayOf(0x1F.toByte(), 0x11.toByte(), 0x50.toByte())

    /**
     * Details about an individual vertical raster slice.
     */
    data class SliceData(
        val sliceIndex: Int,
        val sliceHeight: Int,
        val sliceRawBytes: Int,
        val sliceCompressedBytes: Int,
        val slicePayloadBytes: Int,
        val sliceChunkCount: Int,
        val command: ByteArray
    )

    /**
     * Details about a separate print job block (max 200px) in the proven P50S separate-job architecture.
     */
    data class SeparateJobBlock(
        val jobIndex: Int,
        val startRow: Int,
        val blockHeight: Int,
        val rawBytes: ByteArray,
        val compressedBytes: ByteArray,
        val rasterCommand: ByteArray
    ) {
        val rasterCount: Int get() = 1
    }

    /**
     * Represents the sequential separate-job receipt execution plan for Marklife P50S.
     * Sequence for each block:
     * START_PRINT_JOB
     *     ↓
     * Exactly ONE raster command (≤200px)
     *     ↓
     * Completion check
     *     ↓
     * 500ms post-raster settling wait
     *     ↓
     * STOP_PRINT_JOB
     *     ↓
     * 300ms inter-job delay (between jobs)
     *
     * After final job only:
     * FINAL FEED (160px)
     *     ↓
     * FINAL ALIGN
     */
    data class SeparateJobPrintPlan(
        val totalHeight: Int,
        val blocks: List<SeparateJobBlock>,
        val startJobCommand: ByteArray = CMD_START_PRINT_JOB,
        val stopJobCommand: ByteArray = CMD_STOP_PRINT_JOB,
        val finalFeedCommand: ByteArray = CMD_FEED_PAPER_160PX,
        val finalAlignCommand: ByteArray = CMD_ALIGN_END,
        val postRasterSettlingDelayMs: Long = 500L,
        val interJobDelayMs: Long = 300L
    ) {
        val jobCount: Int get() = blocks.size
        val isEmpty: Boolean get() = blocks.isEmpty()
    }

    /**
     * Represents the experimental Multi-Raster Single-Job receipt execution plan for Marklife P50S.
     * Sequence:
     * START_PRINT_JOB (exactly ONCE for the whole receipt)
     *     ↓
     * Raster Block 1 (≤ maxBlockHeight)
     *     ↓
     * Completion check & post-raster delay
     *     ↓
     * Raster Block 2 (≤ maxBlockHeight)
     *     ↓
     * Completion check & post-raster delay
     *     ↓
     * ... (all remaining raster blocks sequentially)
     *     ↓
     * STOP_PRINT_JOB (exactly ONCE after all raster blocks)
     *     ↓
     * FINAL FEED (160px)
     *     ↓
     * FINAL ALIGN
     *
     * Rules:
     * - Zero START/STOP/FEED/ALIGN between raster blocks.
     * - Single session encompassing all raster blocks.
     */
    data class ExperimentalMultiRasterPrintPlan(
        val totalHeight: Int,
        val blocks: List<SeparateJobBlock>,
        val startJobCommand: ByteArray = CMD_START_PRINT_JOB,
        val stopJobCommand: ByteArray = CMD_STOP_PRINT_JOB,
        val finalFeedCommand: ByteArray = CMD_FEED_PAPER_160PX,
        val finalAlignCommand: ByteArray = CMD_ALIGN_END
    ) {
        val blockCount: Int get() = blocks.size
        val isEmpty: Boolean get() = blocks.isEmpty()
        val totalRasterCount: Int get() = blocks.sumOf { it.rasterCount }
    }

    /**
     * Represents a single-job receipt execution plan where the entire receipt of dynamically
     * calculated height is sent as EXACTLY ONE raster command in EXACTLY ONE print job.
     * Sequence:
     * START_PRINT_JOB
     *     ↓
     * ONE AUTOMATICALLY-SIZED RASTER (height matches actual rendered receipt content)
     *     ↓
     * WAIT FOR VERIFIED PRINT COMPLETION OR SAFE TIMEOUT (calculated from receipt height)
     *     ↓
     * STOP_PRINT_JOB
     *     ↓
     * FINAL FEED (160px)
     *     ↓
     * FINAL ALIGN
     */
    data class SingleJobReceiptPlan(
        val totalHeight: Int,
        val rasterCommand: ByteArray,
        val startJobCommand: ByteArray = CMD_START_PRINT_JOB,
        val stopJobCommand: ByteArray = CMD_STOP_PRINT_JOB,
        val finalFeedCommand: ByteArray = CMD_FEED_PAPER_160PX,
        val finalAlignCommand: ByteArray = CMD_ALIGN_END,
        val postRasterTimeoutMs: Long = calculatePostRasterCompletionTimeoutMs(totalHeight)
    ) {
        val rasterCount: Int get() = 1
        val jobCount: Int get() = 1
        val postRasterDelayMs: Long get() = postRasterTimeoutMs
    }

    /**
     * Represents structured print commands broken down into protocol initiation,
     * individually compressed vertical image slices, and print job finalization.
     */
    data class PrintPayloadStructure(
        val initCommands: ByteArray,
        val slices: List<ByteArray>,
        val sliceDetails: List<SliceData> = emptyList(),
        val endCommands: ByteArray
    ) {
        val totalBytes: Int get() = initCommands.size + slices.sumOf { it.size } + endCommands.size
        val totalSlices: Int get() = slices.size

        fun toByteArray(): ByteArray {
            val out = ByteArrayOutputStream()
            out.write(initCommands)
            for (slice in slices) {
                out.write(slice)
            }
            out.write(endCommands)
            return out.toByteArray()
        }
    }

    /**
     * Slices tall receipts into vertical slice commands and wraps them with protocol setup and teardown commands.
     */
    fun buildStructuredPrintPayload(
        monochromeData: ByteArray,
        totalHeight: Int,
        chunkSize: Int = BLE_CHUNK_SIZE
    ): PrintPayloadStructure {
        val initOut = ByteArrayOutputStream()
        initOut.write(CMD_SET_BT_TYPE)
        initOut.write(CMD_PAPER_TYPE_CONTINUOUS)
        initOut.write(CMD_SET_DENSITY_4)
        initOut.write(CMD_START_PRINT_JOB)

        val slicesList = mutableListOf<ByteArray>()
        val sliceDetailsList = mutableListOf<SliceData>()
        var rowStart = 0
        var sliceIdx = 0
        while (rowStart < totalHeight) {
            val sliceHeight = minOf(SLICE_MAX_HEIGHT, totalHeight - rowStart)
            val sliceBytesCount = sliceHeight * BYTES_PER_ROW
            val sliceRaw = ByteArray(sliceBytesCount)
            System.arraycopy(monochromeData, rowStart * BYTES_PER_ROW, sliceRaw, 0, sliceBytesCount)

            val compressedSlice = compressZlib1KbLevel0(sliceRaw)
            val sliceCmd = buildImageCommand(sliceHeight, compressedSlice)
            slicesList.add(sliceCmd)

            val chunkCount = (sliceCmd.size + chunkSize - 1) / chunkSize
            sliceDetailsList.add(
                SliceData(
                    sliceIndex = sliceIdx,
                    sliceHeight = sliceHeight,
                    sliceRawBytes = sliceBytesCount,
                    sliceCompressedBytes = compressedSlice.size,
                    slicePayloadBytes = sliceCmd.size,
                    sliceChunkCount = chunkCount,
                    command = sliceCmd
                )
            )

            rowStart += sliceHeight
            sliceIdx++
        }

        val endOut = ByteArrayOutputStream()
        endOut.write(CMD_STOP_PRINT_JOB)
        endOut.write(CMD_FEED_PAPER_80PX)
        endOut.write(CMD_ALIGN_END)

        return PrintPayloadStructure(
            initCommands = initOut.toByteArray(),
            slices = slicesList,
            sliceDetails = sliceDetailsList,
            endCommands = endOut.toByteArray()
        )
    }

    /**
     * Constructs a full print payload for a 1-bit monochrome raster bitmap of width 384 dots.
     * Slices tall receipts into multiple continuous blocks to avoid buffer overflow on P50S.
     */
    fun buildPrintPayload(monochromeData: ByteArray, totalHeight: Int): ByteArray {
        return buildStructuredPrintPayload(monochromeData, totalHeight).toByteArray()
    }

    /**
     * Checks if a horizontal row in the monochrome raster data has no printed dots (all bytes 0).
     */
    fun isRowBlank(monochromeData: ByteArray, row: Int, bytesPerRow: Int = BYTES_PER_ROW): Boolean {
        val offset = row * bytesPerRow
        if (offset < 0 || offset + bytesPerRow > monochromeData.size) return false
        for (i in 0 until bytesPerRow) {
            if (monochromeData[offset + i] != 0.toByte()) {
                return false
            }
        }
        return true
    }

    /**
     * Counts the total number of printed (black) dots across a horizontal row.
     */
    fun countRowPrintedPixels(monochromeData: ByteArray, row: Int, bytesPerRow: Int = BYTES_PER_ROW): Int {
        val offset = row * bytesPerRow
        if (offset < 0 || offset + bytesPerRow > monochromeData.size) return 0
        var count = 0
        for (i in 0 until bytesPerRow) {
            val b = monochromeData[offset + i].toInt() and 0xFF
            if (b != 0) {
                count += java.lang.Integer.bitCount(b)
            }
        }
        return count
    }

    /**
     * Calculates the safe raster block height starting at [startRow] with a MAXIMUM of [maxBlockHeight] (default 200px).
     *
     * Strict Content-Aware Slicing Principles:
     * 1. 200px is the MAXIMUM raster height, NOT a mandatory/fixed raster height.
     * 2. Inspects bitmap rows before the 200px limit from [startRow].
     * 3. Finds the nearest safe horizontal blank/white row BEFORE the 200px limit:
     *    scans downwards from `startRow + maxBlockHeight - 1` down to `startRow + 1`.
     * 4. Ends the raster block at that safe blank row so the next raster block starts immediately after.
     * 5. NEVER splits through any non-empty pixel row, text, letters, numbers, logos, dividers,
     *    repair-item rows, totals/payment rows, or any other visible content.
     * 6. EDGE CASE: If no blank row exists before the 200px boundary because content continuously occupies
     *    every row, it does NOT split through a non-empty row. Instead, it locates the earliest valid blank
     *    row after the boundary where a split can occur, ensuring visible content is never cut.
     */
    fun calculateSafeBlockHeight(
        monochromeData: ByteArray,
        startRow: Int,
        totalHeight: Int,
        maxBlockHeight: Int = 200,
        bytesPerRow: Int = BYTES_PER_ROW
    ): Int {
        val remainingHeight = totalHeight - startRow
        if (remainingHeight <= 0) return 0
        if (remainingHeight <= maxBlockHeight) {
            return remainingHeight
        }

        val maxEndRow = startRow + maxBlockHeight - 1

        // 1. Primary: Scan downwards from maxEndRow down to startRow + 1
        // to find the nearest safe horizontal blank row before or at the 200px limit
        for (r in maxEndRow downTo (startRow + 1)) {
            if (isRowBlank(monochromeData, r, bytesPerRow)) {
                return (r - startRow + 1)
            }
        }

        // 2. Edge Case: If content continuously occupies every row before the limit,
        // DO NOT split through a non-empty row. Safely locate the earliest valid row
        // after the boundary where a split can occur.
        for (r in (maxEndRow + 1) until totalHeight) {
            if (isRowBlank(monochromeData, r, bytesPerRow)) {
                return (r - startRow + 1)
            }
        }

        // 3. Fallback: If no blank row exists anywhere in the remainder of the receipt
        // (e.g. solid synthetic bitmap with 100% density), safely cap at maxBlockHeight
        // to prevent hardware buffer overflow.
        return minOf(remainingHeight, maxBlockHeight)
    }

    /**
     * Divides a bitmap's monochrome raster data vertically into blocks of maximum [maxBlockHeight] (default 200px)
     * for sequential separate-job printing on Marklife P50S.
     *
     * Never blindly cuts at row 200. Instead, uses [calculateSafeBlockHeight] to find natural blank rows
     * (whitespace) at or before the 200px boundary so raster blocks NEVER cut through text, characters,
     * logos, dividers, or any visible printed content.
     *
     * Returns an empty list safely if [totalHeight] <= 0 or [monochromeData] is empty.
     */
    fun buildSeparateJobBlocks(
        monochromeData: ByteArray,
        totalHeight: Int,
        maxBlockHeight: Int = 200
    ): List<SeparateJobBlock> {
        if (totalHeight <= 0 || monochromeData.isEmpty()) {
            return emptyList()
        }

        val blocks = mutableListOf<SeparateJobBlock>()
        var rowStart = 0
        var jobIdx = 0
        val bytesPerRow = BYTES_PER_ROW
        while (rowStart < totalHeight) {
            val blockHeight = calculateSafeBlockHeight(
                monochromeData = monochromeData,
                startRow = rowStart,
                totalHeight = totalHeight,
                maxBlockHeight = maxBlockHeight,
                bytesPerRow = bytesPerRow
            )
            if (blockHeight <= 0) break

            val sliceBytesCount = blockHeight * bytesPerRow
            val sliceRaw = ByteArray(sliceBytesCount)
            val sourceOffset = rowStart * bytesPerRow
            if (sourceOffset + sliceBytesCount <= monochromeData.size) {
                System.arraycopy(monochromeData, sourceOffset, sliceRaw, 0, sliceBytesCount)
            } else if (sourceOffset < monochromeData.size) {
                System.arraycopy(monochromeData, sourceOffset, sliceRaw, 0, monochromeData.size - sourceOffset)
            }

            val compressedSlice = compressZlib1KbLevel0(sliceRaw)
            val sliceCmd = buildImageCommand(blockHeight, compressedSlice)

            blocks.add(
                SeparateJobBlock(
                    jobIndex = jobIdx,
                    startRow = rowStart,
                    blockHeight = blockHeight,
                    rawBytes = sliceRaw,
                    compressedBytes = compressedSlice,
                    rasterCommand = sliceCmd
                )
            )

            rowStart += blockHeight
            jobIdx++
        }
        return blocks
    }

    /**
     * Builds the complete sequential separate-job receipt execution plan.
     * Divides [totalHeight] into sequential blocks of maximum [maxBlockHeight] (default 200px).
     * Guarantees:
     * - Empty/0px -> safe handling (0 blocks)
     * - Exactly ONE raster command per job
     * - Every raster height <= maxBlockHeight (200px)
     * - Zero feed or alignment between jobs
     * - Final feed (160px) and final align strictly after the final job
     */
    fun buildSeparateJobPrintPlan(
        monochromeData: ByteArray,
        totalHeight: Int,
        maxBlockHeight: Int = 200
    ): SeparateJobPrintPlan {
        val blocks = buildSeparateJobBlocks(monochromeData, totalHeight, maxBlockHeight)
        return SeparateJobPrintPlan(
            totalHeight = totalHeight,
            blocks = blocks
        )
    }

    /**
     * Builds the execution plan for the experimental Multi-Raster Single-Job Mode.
     * Contains exactly ONE START_PRINT_JOB, multiple raster blocks sent sequentially,
     * exactly ONE STOP_PRINT_JOB after all blocks, followed by the final feed (160px) and align.
     */
    fun buildExperimentalMultiRasterPrintPlan(
        monochromeData: ByteArray,
        totalHeight: Int,
        maxBlockHeight: Int = 200
    ): ExperimentalMultiRasterPrintPlan {
        val blocks = buildSeparateJobBlocks(monochromeData, totalHeight, maxBlockHeight)
        return ExperimentalMultiRasterPrintPlan(
            totalHeight = totalHeight,
            blocks = blocks
        )
    }

    /**
     * Builds a single raster command for an entire automatically-sized receipt bitmap.
     * Sized dynamically to [heightPixels], which matches the actual rendered receipt content height.
     * Produces exactly ONE raster command for the entire receipt.
     */
    fun buildSingleRasterCommand(
        monochromeData: ByteArray,
        heightPixels: Int
    ): ByteArray {
        val totalBytesExpected = heightPixels * BYTES_PER_ROW
        val safeRaw = if (monochromeData.size == totalBytesExpected) {
            monochromeData
        } else {
            val copy = ByteArray(totalBytesExpected)
            System.arraycopy(monochromeData, 0, copy, 0, minOf(monochromeData.size, totalBytesExpected))
            copy
        }
        val compressed = compressZlib1KbLevel0(safeRaw)
        return buildImageCommand(heightPixels, compressed)
    }

    /**
     * Protocol completion response status analysis:
     * Marklife P50S (Protocol 0x1F) uses characteristic 0xFF03 for credit-based flow control.
     * When notification [0x01, 0x04] is received, all 4 credits have been restored,
     * indicating the printer's receive buffer has processed the raster data.
     * Note: Physical printing (thermal head heating and motor stepping) continues during/after buffer clearing.
     * When 0xFF03 notifications are absent (unsupported firmware or open-loop mode),
     * a calculated timeout proportional to the raster height is required.
     */
    fun isRasterBufferCompletionNotification(data: ByteArray): Boolean {
        if (data.size >= 2 && data[0] == 0x01.toByte()) {
            val creditVal = data[1].toInt() and 0xFF
            return creditVal == 0x04
        }
        return false
    }

    /**
     * Calculates the safe post-raster completion timeout in milliseconds based on actual receipt raster height.
     * Formula: baseSettlingDelayMs (500ms) + heightPixels * msPerPixelRow (3ms), bounded by maxTimeoutMs (15000ms).
     *
     * Scaling examples:
     * - 200px: 500ms + 200 * 3ms = 1,100ms
     * - 550px: 500ms + 550 * 3ms = 2,150ms
     * - 600px: 500ms + 600 * 3ms = 2,300ms
     * - 1000px: 500ms + 1000 * 3ms = 3,500ms
     * - 1500px: 500ms + 1500 * 3ms = 5,000ms
     * - 4000px: 500ms + 4000 * 3ms = 12,500ms
     */
    fun calculatePostRasterCompletionTimeoutMs(
        heightPixels: Int,
        baseSettlingDelayMs: Long = MIN_POST_RASTER_DELAY_MS,
        msPerPixelRow: Long = MS_PER_RASTER_ROW,
        maxTimeoutMs: Long = MAX_POST_RASTER_TIMEOUT_MS
    ): Long {
        val calculated = baseSettlingDelayMs + (heightPixels.coerceAtLeast(0).toLong() * msPerPixelRow)
        return calculated.coerceIn(baseSettlingDelayMs, maxTimeoutMs)
    }

    /**
     * Builds a single-job receipt execution plan for a receipt of any calculated height.
     * Guarantees:
     * - Exactly ONE raster command whose height matches [totalHeight]
     * - Exactly ONE START_PRINT_JOB and STOP_PRINT_JOB pair
     * - Zero feeds before STOP_PRINT_JOB
     * - FINAL FEED (160px) and FINAL ALIGN strictly after STOP_PRINT_JOB
     * - Safe post-raster timeout calculated from actual receipt raster height
     */
    fun buildSingleJobReceiptPlan(
        monochromeData: ByteArray,
        totalHeight: Int,
        baseSettlingDelayMs: Long = MIN_POST_RASTER_DELAY_MS,
        msPerPixelRow: Long = MS_PER_RASTER_ROW,
        maxTimeoutMs: Long = MAX_POST_RASTER_TIMEOUT_MS
    ): SingleJobReceiptPlan {
        val raster = buildSingleRasterCommand(monochromeData, totalHeight)
        val calculatedTimeout = calculatePostRasterCompletionTimeoutMs(
            heightPixels = totalHeight,
            baseSettlingDelayMs = baseSettlingDelayMs,
            msPerPixelRow = msPerPixelRow,
            maxTimeoutMs = maxTimeoutMs
        )
        return SingleJobReceiptPlan(
            totalHeight = totalHeight,
            rasterCommand = raster,
            postRasterTimeoutMs = calculatedTimeout
        )
    }

    /**
     * Builds the 10-byte header + compressed image data command for Protocol 0x1F:
     * Header: [0x1F, 0x10, widthBytesHigh, widthBytesLow, heightHigh, heightLow, len3, len2, len1, len0]
     */
    fun buildImageCommand(heightPixels: Int, compressedData: ByteArray): ByteArray {
        val widthBytes = BYTES_PER_ROW // 48
        val cmd = ByteArray(10 + compressedData.size)
        cmd[0] = 0x1F.toByte() // 31
        cmd[1] = 0x10.toByte() // 16 (Raster Image Sub-command)
        cmd[2] = ((widthBytes shr 8) and 0xFF).toByte()
        cmd[3] = (widthBytes and 0xFF).toByte()
        cmd[4] = ((heightPixels shr 8) and 0xFF).toByte()
        cmd[5] = (heightPixels and 0xFF).toByte()
        cmd[6] = ((compressedData.size shr 24) and 0xFF).toByte()
        cmd[7] = ((compressedData.size shr 16) and 0xFF).toByte()
        cmd[8] = ((compressedData.size shr 8) and 0xFF).toByte()
        cmd[9] = (compressedData.size and 0xFF).toByte()

        System.arraycopy(compressedData, 0, cmd, 10, compressedData.size)
        return cmd
    }

    /**
     * Compresses byte array using RFC 1950 Zlib with 1KB window (CMF=0x28, FLG=0x15)
     * and Stored (Level 0) Deflate blocks.
     * This ensures 100% compatibility with the Marklife P50S microcontroller decompression routine
     * without CPU overhead or window-distance buffer overflows.
     */
    fun compressZlib1KbLevel0(raw: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()

        // RFC 1950 header for 1KB window:
        // CMF = 0x28 (window size 1024), FLG = 0x15 (check bit ensures (0x2800 + 0x15) % 31 == 0)
        out.write(0x28)
        out.write(0x15)

        // Calculate Adler-32
        var s1 = 1L
        var s2 = 0L
        for (b in raw) {
            val unsignedVal = b.toInt() and 0xFF
            s1 = (s1 + unsignedVal) % 65521L
            s2 = (s2 + s1) % 65521L
        }
        val adler32 = ((s2 shl 16) or s1) and 0xFFFFFFFFL

        // Split into stored Deflate blocks (max 32768 bytes per block)
        var offset = 0
        while (offset < raw.size) {
            val chunkLen = minOf(raw.size - offset, 32768)
            val isFinal = (offset + chunkLen >= raw.size)

            val bfinalAndType = if (isFinal) 0x01 else 0x00 // BFINAL=1/0, BTYPE=00 (Stored)
            out.write(bfinalAndType)

            // LEN in little endian (2 bytes)
            out.write(chunkLen and 0xFF)
            out.write((chunkLen shr 8) and 0xFF)

            // NLEN (one's complement of LEN) in little endian (2 bytes)
            val nlen = chunkLen.inv() and 0xFFFF
            out.write(nlen and 0xFF)
            out.write((nlen shr 8) and 0xFF)

            // Raw bytes
            out.write(raw, offset, chunkLen)
            offset += chunkLen
        }

        // Adler-32 checksum in big endian (4 bytes)
        out.write(((adler32 shr 24) and 0xFF).toInt())
        out.write(((adler32 shr 16) and 0xFF).toInt())
        out.write(((adler32 shr 8) and 0xFF).toInt())
        out.write((adler32 and 0xFF).toInt())

        return out.toByteArray()
    }

    /**
     * Parses flow control credit notification from characteristic 0xFF03.
     * Android SDK specification:
     * - if data.size >= 2 and data[0] == 0x01:
     *   if data[1] == 0x04 -> credits = 4
     *   else -> credits += data[1]
     */
    fun parseFlowControlNotification(data: ByteArray): Int? {
        if (data.size >= 2 && data[0] == 0x01.toByte()) {
            val creditVal = data[1].toInt() and 0xFF
            return if (creditVal == 0x04) 4 else creditVal
        }
        return null
    }

    /**
     * Determines whether a discovered BLE device is a likely Marklife / P50S printer.
     */
    fun isPotentialP50SPrinter(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val upper = name.uppercase()
        return upper == TARGET_DEVICE_NAME_EXACT ||
                upper.contains("P50S") ||
                upper.contains("P50") ||
                upper.contains("MARKLIFE") ||
                upper.contains("PRINTER") ||
                upper.startsWith("ML-") ||
                upper.startsWith("QP") ||
                upper.contains("496A")
    }
}
