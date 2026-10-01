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
        void setTheme (juce::Colour panel, juce::Colour knob,
                       juce::Colour text, juce::Colour accent);

        void drawRotarySlider (juce::Graphics&, int, int, int, int, float,
                               float, float, juce::Slider&) override;
        void drawButtonBackground (juce::Graphics&, juce::Button&,
                                   const juce::Colour&, bool, bool) override;
        void drawLinearSlider (juce::Graphics&, int, int, int, int,
                               float, float, float,
                               juce::Slider::SliderStyle, juce::Slider&) override;

        juce::Colour panelColour { juce::Colour::fromRGB (218, 198, 165) };
        juce::Colour knobColour { juce::Colour::fromRGB (224, 111, 36) };
        juce::Colour textColour { juce::Colour::fromRGB (18, 18, 16) };
        juce::Colour accentColour { juce::Colour::fromRGB (187, 79, 22) };
    };

    class CallbackColourSelector : public juce::ColourSelector,
                                   private juce::ChangeListener
    {
    public:
        CallbackColourSelector (juce::Colour initial,
                                std::function<void(juce::Colour)> callbackIn);
        ~CallbackColourSelector() override;

    private:
        void changeListenerCallback (juce::ChangeBroadcaster*) override;
        std::function<void(juce::Colour)> callback;
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

    void showThemeMenu();
    void showColourPicker (const juce::Identifier& property,
                           juce::Colour current,
                           juce::Component& anchor);
    void setThemePreset (int presetId);
    void applyThemeFromState (bool force = false);
    juce::Colour getThemeColour (const juce::Identifier& property,
                                 juce::Colour fallback) const;
    void setThemeColour (const juce::Identifier& property, juce::Colour colour);

    void savePreset();
    void loadPreset();
    void resetToDefaults();

    ThreeRDIAnalogPercussionAudioProcessor& processor;
    AnalogLookAndFeel look;

    std::vector<std::unique_ptr<juce::Slider>> knobs;
    std::vector<std::unique_ptr<juce::Label>> knobLabels;
    std::vector<std::unique_ptr<SliderAttachment>> knobAttachments;

    juce::Slider tempoSlider;
    juce::Slider driveSlider;
    std::unique_ptr<SliderAttachment> tempoAttachment;
    std::unique_ptr<SliderAttachment> driveAttachment;

    juce::TextButton themeButton { "THEME" };
    juce::TextButton saveButton { "SAVE" };
    juce::TextButton loadButton { "LOAD" };
    juce::TextButton prefsButton { "PREFERENCES" };
    juce::TextButton runButton { "STOP" };
    juce::TextButton trigButton { "TRIG" };
    juce::TextButton randButton { "RAND" };

    std::unique_ptr<ButtonAttachment> hostSyncAttachment;
    std::unique_ptr<ButtonAttachment> runAttachment;
    std::unique_ptr<juce::FileChooser> presetChooser;

    int64 lastThemeSignature = 0;

    JUCE_DECLARE_NON_COPYABLE_WITH_LEAK_DETECTOR (ThreeRDIAnalogPercussionAudioProcessorEditor)
};
