#pragma once
#include <JuceHeader.h>
#include "PluginProcessor.h"

class ThreeRDIAnalogPercussionAudioProcessorEditor
    : public juce::AudioProcessorEditor,
      private juce::Timer
{
public:
    explicit ThreeRDIAnalogPercussionAudioProcessorEditor (ThreeRDIAnalogPercussionAudioProcessor&);
    ~ThreeRDIAnalogPercussionAudioProcessorEditor() override;

    void paint (juce::Graphics&) override;
    void resized() override;

private:
    class AnalogLookAndFeel : public juce::LookAndFeel_V4
    {
    public:
        void drawRotarySlider (juce::Graphics&, int, int, int, int, float,
                               float, float, juce::Slider&) override;
        void drawButtonBackground (juce::Graphics&, juce::Button&,
                                   const juce::Colour&, bool, bool) override;
        void drawLinearSlider (juce::Graphics&, int, int, int, int,
                               float, float, float,
                               juce::Slider::SliderStyle, juce::Slider&) override;
    };

    using SliderAttachment = juce::AudioProcessorValueTreeState::SliderAttachment;
    using ButtonAttachment = juce::AudioProcessorValueTreeState::ButtonAttachment;

    void addKnob (const juce::String& paramId,
                  const juce::String& labelText,
                  juce::Rectangle<int> bounds,
                  const juce::String& suffix,
                  int decimals,
                  bool small = false);

    void timerCallback() override;

    ThreeRDIAnalogPercussionAudioProcessor& processor;
    AnalogLookAndFeel look;

    std::vector<std::unique_ptr<juce::Slider>> knobs;
    std::vector<std::unique_ptr<juce::Label>> knobLabels;
    std::vector<std::unique_ptr<SliderAttachment>> knobAttachments;

    juce::Slider tempoSlider;
    juce::Slider driveSlider;
    std::unique_ptr<SliderAttachment> tempoAttachment;
    std::unique_ptr<SliderAttachment> driveAttachment;

    juce::TextButton prefsButton { "PREFERENCES" };
    juce::TextButton runButton { "STOP" };
    juce::TextButton trigButton { "TRIG" };
    juce::TextButton randButton { "RAND" };

    std::unique_ptr<ButtonAttachment> hostSyncAttachment;
    std::unique_ptr<ButtonAttachment> runAttachment;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (ThreeRDIAnalogPercussionAudioProcessorEditor)
};
