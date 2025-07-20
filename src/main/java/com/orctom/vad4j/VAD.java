package com.orctom.vad4j;

import com.orctom.vad4j.exception.VADException;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;

public class VAD implements Closeable {

    public static final int QUALITY = 0;
    public static final int LOW_BITRATE = 1;
    public static final int AGGRESSIVE = 2;
    public static final int VERY_AGGRESSIVE = 3;
    public static final int CODE_SUCCESS = 0;
    public static final int CODE_ERROR = -1;
    public static final int ACTIVE_VOICE = 1;
    public static final int NONACTIVE_VOICE = 0;
    public static final int INVALID_FRAME = -1;
    private static final Logger LOGGER = LoggerFactory.getLogger(VAD.class);
    private AtomicBoolean stopped = new AtomicBoolean(false);

    private Pointer state;

    public VAD() {
        state = Detector.INSTANCE.fvad_new();
    }

    public VAD(int sampleRate, int mode) {
        state = Detector.INSTANCE.fvad_new();
        int result = Detector.INSTANCE.fvad_set_mode(state, mode);
        if (CODE_SUCCESS != result) {
            throw new VADException("Failed to init VAD");
        }
        result = Detector.INSTANCE.fvad_set_sample_rate(state, sampleRate);
        if (CODE_SUCCESS != result) {
            throw new VADException("Failed to init VAD");
        }
    }

    public int speech(byte[] pcm) {
        if (null == pcm) {
            return INVALID_FRAME;
        }

        short[] frame = Bytes.toShortArray(pcm);
        try {
            int result = Detector.INSTANCE.fvad_process(state, frame, frame.length);
            LOGGER.trace("result: {}", result);
            return result;
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            return INVALID_FRAME;
        }
    }

    public boolean isSpeech(byte[] pcm) {
        return speech(pcm) == ACTIVE_VOICE;
    }

    public boolean isSilent(byte[] pcm) {
        return speech(pcm) == NONACTIVE_VOICE;
    }

    @Override
    public void close() {
        if (stopped.getAndSet(true)) {
            return;
        }

        LOGGER.info("closing VAD");
        Detector.INSTANCE.fvad_free(state);
    }

    public interface Detector extends Library {

        Detector INSTANCE = Native.loadLibrary("kvad", Detector.class);

        /**
         * Creates and initializes a VAD instance.
         * <p>
         * On success, returns a pointer to the new VAD instance, which should
         * eventually be deleted using fvad_free().
         * <p>
         * Returns NULL in case of a memory allocation error.
         */
        Pointer fvad_new();

        /**
         * Changes the VAD operating ("aggressiveness") mode of a VAD instance.
         * <p>
         * A more aggressive (higher mode) VAD is more restrictive in reporting speech.
         * Put in other words the probability of being speech when the VAD returns 1 is
         * increased with increasing mode. As a consequence also the missed detection
         * rate goes up.
         * <p>
         * Valid modes are 0 ("quality"), 1 ("low bitrate"), 2 ("aggressive"), and 3
         * ("very aggressive"). The default mode is 0.
         * <p>
         * Returns 0 on success, or -1 if the specified mode is invalid.
         */
        int fvad_set_mode(Pointer vadDetector, int mode);


        /**
         * Reinitializes a VAD instance, clearing all state and resetting mode and
         * sample rate to defaults.
         */
        int fvad_reset(Pointer vadDetector);

        /**
         * Calculates a VAD decision for an audio frame.
         * <p>
         * `frame` is an array of `length` signed 16-bit samples. Only frames with a
         * length of 10, 20 or 30 ms are supported, so for example at 8 kHz, `length`
         * must be either 80, 160 or 240.
         * <p>
         * Returns              : 1 - (active voice),
         * 0 - (non-active Voice),
         * -1 - (invalid frame length).
         */
        int fvad_process(Pointer inst, short[] frame, int length);

        /**
         * Sets the input sample rate in Hz for a VAD instance.
         * <p>
         * Valid values are 8000, 16000, 32000 and 48000. The default is 8000. Note
         * that internally all processing will be done 8000 Hz; input data in higher
         * sample rates will just be downsampled first.
         * <p>
         * Returns 0 on success, or -1 if the passed value is invalid.
         */
        int fvad_set_sample_rate(Pointer inst, int sampleRate);


        /**
         * Frees the dynamic memory of a specified VAD instance.
         */
        void fvad_free(Pointer vadDetector);
    }
}
