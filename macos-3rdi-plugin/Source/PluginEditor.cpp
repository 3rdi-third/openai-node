#include "PluginEditor.h"

ThreeRDIAnalogPercussionAudioProcessorEditor::ThreeRDIAnalogPercussionAudioProcessorEditor
    (ThreeRDIAnalogPercussionAudioProcessor& p)
    : AudioProcessorEditor (&p), processor (p)
{
    generic = std::make_unique<juce::GenericAudioProcessorEditor> (processor);
    addAndMakeVisible (*generic);
    setResizable (true, true);
    setSize (720, 760);
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::paint (juce::Graphics& g)
{
    g.fillAll (juce::Colour::fromRGB (218, 198, 165));
}

void ThreeRDIAnalogPercussionAudioProcessorEditor::resized()
{
    generic->setBounds (getLocalBounds());
}
