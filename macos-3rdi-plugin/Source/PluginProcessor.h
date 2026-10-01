#pragma once
#include <JuceHeader.h>

class ThreeRDIAnalogPercussionAudioProcessor : public juce::AudioProcessor
{
public:
    ThreeRDIAnalogPercussionAudioProcessor();
    ~ThreeRDIAnalogPercussionAudioProcessor() override = default;

    void prepareToPlay (double sampleRate, int samplesPerBlock) override;
    void releaseResources() override {}
    bool isBusesLayoutSupported (const BusesLayout& layouts) const override;
    void processBlock (juce::AudioBuffer<float>&, juce::MidiBuffer&) override;

    juce::AudioProcessorEditor* createEditor() override;
    bool hasEditor() const override { return true; }

    const juce::String getName() const override { return JucePlugin_Name; }
    bool acceptsMidi() const override { return true; }
    bool producesMidi() const override { return false; }
    bool isMidiEffect() const override { return false; }
    double getTailLengthSeconds() const override { return 3.0; }

    int getNumPrograms() override { return 1; }
    int getCurrentProgram() override { return 0; }
    void setCurrentProgram (int) override {}
    const juce::String getProgramName (int) override { return {}; }
    void changeProgramName (int, const juce::String&) override {}

    void getStateInformation (juce::MemoryBlock&) override;
    void setStateInformation (const void*, int) override;

    juce::AudioProcessorValueTreeState apvts;
    static juce::AudioProcessorValueTreeState::ParameterLayout createParameterLayout();

private:
    void triggerStep (int stepIndex, float noteHz = -1.0f);
    float envCoeff (float ms) const noexcept;
    float getHostTempoAndState (bool& hostPlaying, double& ppq);

    double sr = 48000.0;
    double phase1 = 0.0, phase2 = 0.0, lfoPhase = 0.0;
    double samplesToNextStep = 0.0;
    float envAmp = 0.0f, envFilter = 0.0f, velocity = 1.0f;
    float baseHz = 62.0f;
    float z1 = 0, z2 = 0, z3 = 0, z4 = 0;
    int step = 0;
    int lastHostStep = -1;
    juce::Random rng;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (ThreeRDIAnalogPercussionAudioProcessor)
};
