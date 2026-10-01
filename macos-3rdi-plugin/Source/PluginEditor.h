#pragma once
#include <JuceHeader.h>
#include "PluginProcessor.h"

class ThreeRDIAnalogPercussionAudioProcessorEditor : public juce::AudioProcessorEditor
{
public:
    explicit ThreeRDIAnalogPercussionAudioProcessorEditor (ThreeRDIAnalogPercussionAudioProcessor&);
    ~ThreeRDIAnalogPercussionAudioProcessorEditor() override = default;

    void paint (juce::Graphics&) override;
    void resized() override;

private:
    ThreeRDIAnalogPercussionAudioProcessor& processor;
    std::unique_ptr<juce::AudioProcessorEditor> generic;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (ThreeRDIAnalogPercussionAudioProcessorEditor)
};
