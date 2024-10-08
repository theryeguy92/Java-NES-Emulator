package core.apu;

import core.NES;
import core.apu.channels.DMCChannel;
import core.apu.channels.NoiseChannel;
import core.apu.channels.PulseChannel;
import core.apu.channels.TriangleChannel;
import utils.AudioSampleCollection;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Here we make the APU (Audio Processing Unit) This will handle all of the sounds made by the NES games.
 */
public class APU_2A03 {

    private static final int VISUALIZER_SAMPLE_SIZE = 256;
    private static final double CLOCK_TIME = .333333333 / 1789773.0;

    private static double volume = 1;
    private static int skip_audio_sample = 2;

    public static final int[] length_table = {
            10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60,
            10, 14, 12, 26, 14, 12, 16, 24, 18, 48, 20, 96, 22, 192, 24,
            72, 26, 16, 28, 32, 30};

    private final PulseChannel pulse_1;
    private final PulseChannel pulse_2;
    private final TriangleChannel triangle;
    private final NoiseChannel noise;
    private final DMCChannel dmc;
    private final Queue<AudioSampleCollection> audio_visualizer_queue;

    private int clock_counter = 0;
    private double total_time = 0.0;
    private int frame_counter = 0;
    private int cycle_remaining_since_4017_write = -1;

    private boolean frame_IRQ = false;
    private boolean flag_IRQ_inhibit = false;
    private boolean flag_5_step_mode = false;

    private boolean raw_audio = false;
    private boolean pulse_1_rendered = true;
    private boolean pulse_2_rendered = true;
    private boolean noise_rendered = true;
    private boolean triangle_rendered = true;
    private boolean dmc_rendered = true;
    private boolean linear_out = false;

    private int cycles_until_visualizer_sample = 0;
    private int audio_sample_until_skip = 0;

    /**
     * Create a new instance of an APU
     */
    public APU_2A03(NES nes) {
        pulse_1 = new PulseChannel();
        pulse_2 = new PulseChannel();
        triangle = new TriangleChannel();
        noise = new NoiseChannel();
        dmc = new DMCChannel(nes);
        audio_visualizer_queue = new ConcurrentLinkedQueue<>();
    }

    /**
     * Set the master volume of the APU
     *
     * @param volume the volume to set between 0 and 1
     */
    public static void setVolume(double volume) {
        APU_2A03.volume = volume;
    }

    /**
     * Return the current volume of the APU
     *
     * @return the current APU volume
     */
    public static double getVolume() {
        return volume;
    }

    public static void setSampleSkip(int sampleSkip) {
        skip_audio_sample = sampleSkip;
    }

    /**
     * Return the current audio sample as a value between -1 and 1
     *
     * @return the current audio sample as a value between -1 and 1
     */
    public double getSample(boolean update_visual) {
        double sample;
        double p1 = pulse_1_rendered ? pulse_1.sample * 15 : 0;
        double p2 = pulse_2_rendered ? pulse_2.sample * 15 : 0;
        double t = triangle_rendered ? triangle.sample * 15 : 0;
        double n = noise_rendered ? noise.sample * 15 : 0;
        double d = dmc_rendered ? dmc.output * 128 : 0;
        if (linear_out)
            sample = (0.00752 * (p1 + p2) + 0.00851 * t + 0.00494 * n + 0.00335 * d) * 1.5;
        else {
            double pulse = (p1 + p2 == 0) ? 0 : 95.88 / ((8128.0 / (p1 + p2)) + 100);
            double tnd = (t == 0 && n == 0 && d == 0) ? 0 : 159.79 / (1.0 / (t / 8227.0 + n / 12241.0 + d / 22638.0) + 100);
            sample = pulse + tnd;
        }

        if (update_visual) {
            if (cycles_until_visualizer_sample == 0) {
                if (audio_visualizer_queue.size() >= VISUALIZER_SAMPLE_SIZE)
                    audio_visualizer_queue.poll();

                AudioSampleCollection sampleCollection = new AudioSampleCollection();
                sampleCollection.pulse1 = pulse_1_rendered ? pulse_1.sample : 0;
                sampleCollection.pulse2 = pulse_2_rendered ? pulse_2.sample : 0;
                sampleCollection.triangle = triangle_rendered ? triangle.sample : 0;
                sampleCollection.noise = noise_rendered ? noise.sample : 0;
                sampleCollection.dmc = dmc_rendered ? dmc.output : 0;
                sampleCollection.mixer = sample * 1.5;
                audio_visualizer_queue.offer(sampleCollection);

                cycles_until_visualizer_sample = 1280 / VISUALIZER_SAMPLE_SIZE;
            }
            cycles_until_visualizer_sample--;
        }
        return sample * 2 * volume;
    }

    public Queue<AudioSampleCollection> getAudioVisualizerQueue() {
        return audio_visualizer_queue;
    }

    /**
     * Enable or Disable the first pulse channel rendering
     * (only inhibit the output of the channel to the Mixer)
     *
     * @param enabled should the channel be rendered
     */
    public void setPulse1Rendered(boolean enabled) {
        this.pulse_1_rendered = enabled;
    }

    /**
     * Enable or Disable the second pulse channel rendering
     * (only inhibit the output of the channel to the Mixer)
     *
     * @param enabled should the channel be rendered
     */
    public void setPulse2Rendered(boolean enabled) {
        this.pulse_2_rendered = enabled;
    }

    /**
     * Enable or Disable the noise channel rendering
     * (only inhibit the output of the channel to the Mixer)
     *
     * @param enabled should the channel be rendered
     */
    public void setNoiseRendered(boolean enabled) {
        this.noise_rendered = enabled;
    }

    /**
     * Enable or Disable the triangle channel rendering
     * (only inhibit the output of the channel to the Mixer)
     *
     * @param enabled should the channel be rendered
     */
    public void setTriangleRendered(boolean enabled) {
        this.triangle_rendered = enabled;
    }

    /**
     * Enable or Disable the DMC channel rendering
     * (only inhibit the output of the channel to the Mixer)
     *
     * @param enabled should the channel be rendered
     */
    public void setDMCRendered(boolean enabled) {
        this.dmc_rendered = enabled;
    }

    /**
     * Reset the APU
     */
    public void reset() {
        cpuWrite(0x4015, 0x00);
    }

    /**
     * Set the APU to startup
     */
    public void startup() {
        for (int i = 0x4000; i < 0x4007; i++)
            cpuWrite(i, 0x00);
        for (int i = 0x4010; i < 0x4013; i++)
            cpuWrite(i, 0x00);
        noise.setSequence(0xDBDB);
        frame_counter = 15;
        cpuWrite(0x4015, 0x00);
    }

    /**
     * Called when the CPU try to write to the APU
     *
     * @param addr the address to write to
     */
    public void cpuWrite(int addr, int data) {
        //If bit 7 is set quarter and half frame signals ar triggered
        switch (addr) {
            case 0x4000 -> pulse_1.writeDutyCycle(data);
            case 0x4001 -> pulse_1.writeSweep(data);
            case 0x4002 -> pulse_1.writeTimerLow(data);
            case 0x4003 -> {
                pulse_1.writeTimerHigh(data);
                pulse_1.writeLengthCounter(data);
            }
            case 0x4004 -> pulse_2.writeDutyCycle(data);
            case 0x4005 -> pulse_2.writeSweep(data);
            case 0x4006 -> pulse_2.writeTimerLow(data);
            case 0x4007 -> {
                pulse_2.writeTimerHigh(data);
                pulse_2.writeLengthCounter(data);
            }
            case 0x4008 -> triangle.writeLinearCounter(data);
            case 0x400A -> triangle.writeTimerLow(data);
            case 0x400B -> {
                triangle.writeTimerHigh(data);
                triangle.loadLengthCounter(data);
            }
            case 0x400C -> noise.writeEnvelope(data);
            case 0x400E -> noise.updateReload(data);
            case 0x400F -> noise.writeLengthCounter(data);
            case 0x4010 -> dmc.writeRate(data);
            case 0x4011 -> dmc.directLoad(data);
            case 0x4012 -> dmc.writeSampleAddr(data);
            case 0x4013 -> dmc.writeSampleLength(data);
            case 0x4015 -> {
                pulse_1.enable(false);
                pulse_2.enable(false);
                triangle.enable(false);
                noise.enable(false);
                dmc.clearIrq();
                if ((data & 0x10) == 0x00)
                    dmc.clearReader();
                if ((data & 0x1) == 0x1)
                    pulse_1.enable(true);
                else
                    pulse_1.resetLengthCounter();
                if ((data & 0x2) == 0x2)
                    pulse_2.enable(true);
                else
                    pulse_2.resetLengthCounter();
                if ((data & 0x4) == 0x4)
                    triangle.enable(true);
                else {
                    triangle.resetLengthCounter();
                    triangle.resetLinearCounter();
                }
                if ((data & 0x8) == 0x8)
                    noise.enable(true);
                else
                    noise.resetLengthCounter();
            }
            case 0x4017 -> {
                flag_5_step_mode = (data & 0x80) == 0x80;
                flag_IRQ_inhibit = (data & 0x40) == 0x40;
                if (flag_IRQ_inhibit) frame_IRQ = false;
                cycle_remaining_since_4017_write = 4;
                if (flag_5_step_mode) {
                    pulse_1.clockLengthCounter();
                    pulse_1.clockEnvelope();
                    pulse_1.clockSweeper(0);

                    pulse_2.clockLengthCounter();
                    pulse_2.clockEnvelope();
                    pulse_2.clockSweeper(1);

                    triangle.clockLinearCounter();
                    triangle.clockLengthCounter();

                    noise.clockEnvelope();
                    noise.clockLengthCounter();
                }
            }
        }
    }

    /**
     * Called when the CPU try to read from the APU
     * Only address 0x4015 is relevant for the CPU
     *
     * @param addr the address to read from
     * @return the value represent the state of the counters of every implemented channels
     */
    public int cpuRead(int addr, boolean readOnly) {
        int data = 0x00;
        if (addr == 0x4015) {
            data |= (pulse_1.getLengthCounter() > 0) ? 0x01 : 0x00;
            data |= (pulse_2.getLengthCounter() > 0) ? 0x02 : 0x00;
            data |= (triangle.getLengthCounter() > 0) ? 0x04 : 0x00;
            data |= (noise.getLengthCounter() > 0) ? 0x08 : 0x00;
            data |= (dmc.hasBytesLeft()) ? 0x10 : 0x00;
            data |= (dmc.hasInterruptTriggered()) ? 0x80 : 0x00;
            data |= frame_IRQ ? 0x40 : 0x00;
        }
        if (!readOnly)
            frame_IRQ = false;
        return data;
    }

    /**
     * A system clock of the APU, sampling can be deactivated for better performance
     * but there will be no sound
     * when sampling is disabled we only update what is susceptible to be read (the length counters)
     *
     * @param enable_sampling if sampling is enabled
     */
    public void clock(boolean enable_sampling, double timePerClock) {
        boolean quarter_frame = false;
        boolean half_frame = false;

        total_time += CLOCK_TIME;
        if (clock_counter % 3 == 0) {
            dmc.clock();
            if (enable_sampling)
                triangle.computeSample(timePerClock / 3, raw_audio);
            if (clock_counter % 6 == 0) {
                //A write to 0x4017 will cause the frame counter to be reset after 4 CPU cycles (2 APU cycles)
                if (cycle_remaining_since_4017_write == 0) {
                    frame_counter = 0;
                    cycle_remaining_since_4017_write = -1;
                }
                if (cycle_remaining_since_4017_write >= 0)
                    cycle_remaining_since_4017_write -= 2;

                frame_counter++;
                if (flag_5_step_mode) {
                    quarter_frame = frame_counter == 3729 || frame_counter == 7457 || frame_counter == 11186 || frame_counter == 18641;
                    half_frame = frame_counter == 7457 || frame_counter == 18641;
                    if (frame_counter == 18641)
                        frame_counter = 0;
                } else {
                    quarter_frame = frame_counter == 3729 || frame_counter == 7457 || frame_counter == 11186 || frame_counter == 14916;
                    half_frame = frame_counter == 7457 || frame_counter == 14916;
                    if (frame_counter == 14916) {
                        frame_counter = 0;
                        if (!flag_IRQ_inhibit)
                            frame_IRQ = true;
                    }
                }
                if (quarter_frame) {
                    triangle.clockLinearCounter();
                    if (enable_sampling) {
                        pulse_1.clockEnvelope();
                        pulse_2.clockEnvelope();
                        noise.clockEnvelope();
                    }
                }
                if (half_frame) {
                    pulse_1.clockLengthCounter();
                    pulse_2.clockLengthCounter();
                    triangle.clockLengthCounter();
                    noise.clockLengthCounter();
                    pulse_1.clockSweeper(0);
                    pulse_2.clockSweeper(1);
                }
                if (enable_sampling) {
                    if (audio_sample_until_skip >= skip_audio_sample) {
                        pulse_1.computeSample(total_time, raw_audio);
                        pulse_2.computeSample(total_time, raw_audio);
                        noise.computeSample();
                        dmc.computeSample();
                        audio_sample_until_skip = 0;
                    }
                    audio_sample_until_skip++;
                }
            }
        }
        pulse_1.trackSweeper();
        pulse_2.trackSweeper();

        clock_counter++;
    }

    /**
     * Enable or Disable RAW Audio mode
     *
     * @param raw should RAW Audio be triggered or not
     */
    public void enabledRawMode(boolean raw) {
        raw_audio = raw;
    }

    /**
     * Return whether or not the APU need to trigger an IRQ
     * If an IRQ is triggered, the source can be determined by reading from 0x4015
     *
     * @return Does the APU need to trigger an IRQ
     */
    public boolean irq() {
        return frame_IRQ || dmc.hasInterruptTriggered();
    }

    /**
     * Return whether or not RAW audio is enabled
     *
     * @return is RAW audio rendering enabled
     */
    public boolean isRAWAudioEnabled() {
        return raw_audio;
    }

    /**
     * Return whether or not Pulse 1 is enabled
     *
     * @return is Pulse 1 rendering enabled
     */
    public boolean isPulse1Rendered() {
        return pulse_1_rendered;
    }

    /**
     * Return whether or not Pulse 2 is enabled
     *
     * @return is Pulse 2 rendering enabled
     */
    public boolean isPulse2Rendered() {
        return pulse_2_rendered;
    }

    /**
     * Return whether or not Triangle is enabled
     *
     * @return is Triangle rendering enabled
     */
    public boolean isTriangleRendered() {
        return triangle_rendered;
    }

    /**
     * Return whether or not Noise is enabled
     *
     * @return is Noise rendering enabled
     */
    public boolean isNoiseRendered() {
        return noise_rendered;
    }

    /**
     * Return whether or not DMC is enabled
     *
     * @return is DMC rendering enabled
     */
    public boolean isDMCRendered() {
        return dmc_rendered;
    }

    /**
     * Return whether or not the mixer is linearly approximated
     *
     * @return is the mixer linearly approximated
     */
    public boolean isLinear() {
        return linear_out;
    }

    public void setLinear(boolean linear) {
        linear_out = linear;
    }
}
